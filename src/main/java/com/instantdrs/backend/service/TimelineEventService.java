package com.instantdrs.backend.service;

import com.instantdrs.backend.dto.TimelineEventRequest;
import com.instantdrs.backend.dto.TimelineEventResponse;
import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.entity.TimelineEvent;
import com.instantdrs.backend.exception.ResourceNotFoundException;
import com.instantdrs.backend.repository.DrsSessionRepository;
import com.instantdrs.backend.repository.TimelineEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimelineEventService {

    private final TimelineEventRepository timelineEventRepository;
    private final DrsSessionRepository drsSessionRepository;

    public TimelineEventService(TimelineEventRepository timelineEventRepository, DrsSessionRepository drsSessionRepository) {
        this.timelineEventRepository = timelineEventRepository;
        this.drsSessionRepository = drsSessionRepository;
    }

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> getTimelineEvents(Long sessionId) {
        // Validate session exists
        if (!drsSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("DrsSession not found with id: " + sessionId);
        }

        List<TimelineEvent> events = timelineEventRepository.findBySessionIdOrderByTimestamp(sessionId);
        return events.stream()
                .map(TimelineEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public TimelineEventResponse createTimelineEvent(Long sessionId, TimelineEventRequest request) {
        DrsSession session = drsSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("DrsSession not found with id: " + sessionId));

        if (request == null) {
            throw new IllegalArgumentException("Timeline event request cannot be null");
        }

        if (request.getEventType() == null || request.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be blank");
        }

        if (request.getTimestampMs() == null || request.getTimestampMs() < 0) {
            throw new IllegalArgumentException("Timestamp in milliseconds must be non-negative");
        }

        if (request.getDurationMs() != null && request.getDurationMs() < 0) {
            throw new IllegalArgumentException("Duration in milliseconds must be non-negative");
        }

        TimelineEvent event = new TimelineEvent(
                session,
                request.getEventType().trim(),
                request.getTimestampMs(),
                request.getDurationMs(),
                request.getDescription()
        );

        TimelineEvent savedEvent = timelineEventRepository.save(event);
        return TimelineEventResponse.fromEntity(savedEvent);
    }

    @Transactional(readOnly = true)
    public TimelineEventResponse getTimelineEvent(Long sessionId, Long eventId) {
        // Validate session exists
        if (!drsSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("DrsSession not found with id: " + sessionId);
        }

        TimelineEvent event = timelineEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Timeline event not found with id: " + eventId));

        // Validate event belongs to the requested session
        if (!event.getSession().getId().equals(sessionId)) {
            throw new ResourceNotFoundException("Timeline event with id " + eventId + " does not belong to session id " + sessionId);
        }

        return TimelineEventResponse.fromEntity(event);
    }
}
