package com.instantdrs.backend.service;

import com.instantdrs.backend.dto.ReviewDecisionRequest;
import com.instantdrs.backend.dto.ReviewDecisionResponse;
import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.entity.ReviewDecision;
import com.instantdrs.backend.entity.TimelineEvent;
import com.instantdrs.backend.exception.ResourceNotFoundException;
import com.instantdrs.backend.repository.DrsSessionRepository;
import com.instantdrs.backend.repository.ReviewDecisionRepository;
import com.instantdrs.backend.repository.TimelineEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewDecisionService {

    private final ReviewDecisionRepository reviewDecisionRepository;
    private final DrsSessionRepository drsSessionRepository;
    private final TimelineEventRepository timelineEventRepository;

    public ReviewDecisionService(
            ReviewDecisionRepository reviewDecisionRepository,
            DrsSessionRepository drsSessionRepository,
            TimelineEventRepository timelineEventRepository) {
        this.reviewDecisionRepository = reviewDecisionRepository;
        this.drsSessionRepository = drsSessionRepository;
        this.timelineEventRepository = timelineEventRepository;
    }

    @Transactional
    public ReviewDecisionResponse saveDecision(Long sessionId, ReviewDecisionRequest request) {
        DrsSession session = drsSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("DrsSession not found with id: " + sessionId));

        if (request == null) {
            throw new IllegalArgumentException("Decision request cannot be null");
        }

        if (request.getDecision() == null || request.getDecision().trim().isEmpty()) {
            throw new IllegalArgumentException("Decision cannot be blank");
        }

        // Validate selectedEventId if provided
        if (request.getSelectedEventId() != null) {
            TimelineEvent event = timelineEventRepository.findById(request.getSelectedEventId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected timeline event not found with id: " + request.getSelectedEventId()));

            if (!event.getSession().getId().equals(sessionId)) {
                throw new IllegalArgumentException("Selected event with id " + request.getSelectedEventId()
                        + " does not belong to session id " + sessionId);
            }
        }

        LocalDateTime reviewedAt = request.getReviewedAt() != null ? request.getReviewedAt() : LocalDateTime.now();

        ReviewDecision decision = new ReviewDecision(
                session,
                request.getDecision().trim(),
                request.getSelectedEventId(),
                request.getNotes(),
                request.getReviewedBy(),
                reviewedAt
        );

        ReviewDecision savedDecision = reviewDecisionRepository.save(decision);

        // Update the session's decision field with the user's selected decision
        session.setDecision(savedDecision.getDecision());
        drsSessionRepository.save(session);

        return ReviewDecisionResponse.fromEntity(savedDecision);
    }

    @Transactional(readOnly = true)
    public ReviewDecisionResponse getLatestDecision(Long sessionId) {
        if (!drsSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("DrsSession not found with id: " + sessionId);
        }

        ReviewDecision decision = reviewDecisionRepository.findLatestBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("No review decision found for session id: " + sessionId));

        return ReviewDecisionResponse.fromEntity(decision);
    }

    @Transactional(readOnly = true)
    public List<ReviewDecisionResponse> getAllDecisionsForSession(Long sessionId) {
        if (!drsSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("DrsSession not found with id: " + sessionId);
        }

        return reviewDecisionRepository.findBySessionIdOrderByReviewedAtDesc(sessionId)
                .stream()
                .map(ReviewDecisionResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
