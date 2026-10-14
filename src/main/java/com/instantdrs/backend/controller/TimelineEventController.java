package com.instantdrs.backend.controller;

import com.instantdrs.backend.dto.TimelineEventRequest;
import com.instantdrs.backend.dto.TimelineEventResponse;
import com.instantdrs.backend.service.TimelineEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drs/sessions/{id}/timeline")
public class TimelineEventController {

    private final TimelineEventService timelineEventService;

    public TimelineEventController(TimelineEventService timelineEventService) {
        this.timelineEventService = timelineEventService;
    }

    @GetMapping
    public ResponseEntity<List<TimelineEventResponse>> getTimelineEvents(@PathVariable("id") Long id) {
        List<TimelineEventResponse> events = timelineEventService.getTimelineEvents(id);
        return ResponseEntity.ok(events);
    }

    @PostMapping
    public ResponseEntity<TimelineEventResponse> createTimelineEvent(
            @PathVariable("id") Long id,
            @Valid @RequestBody TimelineEventRequest request) {
        TimelineEventResponse createdEvent = timelineEventService.createTimelineEvent(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<TimelineEventResponse> getTimelineEvent(
            @PathVariable("id") Long id,
            @PathVariable("eventId") Long eventId) {
        TimelineEventResponse event = timelineEventService.getTimelineEvent(id, eventId);
        return ResponseEntity.ok(event);
    }
}
