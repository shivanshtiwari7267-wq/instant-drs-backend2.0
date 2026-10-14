package com.instantdrs.backend.service;

import com.instantdrs.backend.dto.TimelineEventRequest;
import com.instantdrs.backend.dto.TimelineEventResponse;
import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.entity.TimelineEvent;
import com.instantdrs.backend.exception.ResourceNotFoundException;
import com.instantdrs.backend.repository.DrsSessionRepository;
import com.instantdrs.backend.repository.TimelineEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimelineEventServiceTest {

    @Mock
    private TimelineEventRepository timelineEventRepository;

    @Mock
    private DrsSessionRepository drsSessionRepository;

    private TimelineEventService timelineEventService;

    private DrsSession mockSession;

    @BeforeEach
    void setUp() {
        timelineEventService = new TimelineEventService(timelineEventRepository, drsSessionRepository);

        mockSession = new DrsSession();
        mockSession.setId(1L);
        mockSession.setGame("cricket");
        mockSession.setStatus("COMPLETED");
    }

    @Test
    void createTimelineEvent_Success() {
        TimelineEventRequest request = new TimelineEventRequest("BALL_PITCH", 1500L, 200L, "Ball pitching outside off");

        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));
        when(timelineEventRepository.save(any(TimelineEvent.class))).thenAnswer(invocation -> {
            TimelineEvent event = invocation.getArgument(0);
            event.setId(10L);
            event.setCreatedAt(LocalDateTime.now());
            return event;
        });

        TimelineEventResponse response = timelineEventService.createTimelineEvent(1L, request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(1L, response.getSessionId());
        assertEquals("BALL_PITCH", response.getEventType());
        assertEquals(1500L, response.getTimestampMs());
        assertEquals(200L, response.getDurationMs());
        assertEquals("Ball pitching outside off", response.getDescription());
        assertNotNull(response.getCreatedAt());

        verify(drsSessionRepository).findById(1L);
        verify(timelineEventRepository).save(any(TimelineEvent.class));
    }

    @Test
    void createTimelineEvent_MissingSession_ThrowsNotFound() {
        TimelineEventRequest request = new TimelineEventRequest("BALL_IMPACT", 2000L, null, "Impact on pad");
        when(drsSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                timelineEventService.createTimelineEvent(999L, request)
        );

        verify(timelineEventRepository, never()).save(any());
    }

    @Test
    void createTimelineEvent_BlankEventType_ThrowsBadRequest() {
        TimelineEventRequest request = new TimelineEventRequest("   ", 2000L, null, "Impact");
        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                timelineEventService.createTimelineEvent(1L, request)
        );
        assertEquals("Event type cannot be blank", ex.getMessage());
    }

    @Test
    void createTimelineEvent_NegativeTimestamp_ThrowsBadRequest() {
        TimelineEventRequest request = new TimelineEventRequest("BALL_IMPACT", -10L, null, "Impact");
        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                timelineEventService.createTimelineEvent(1L, request)
        );
        assertEquals("Timestamp in milliseconds must be non-negative", ex.getMessage());
    }

    @Test
    void createTimelineEvent_NegativeDuration_ThrowsBadRequest() {
        TimelineEventRequest request = new TimelineEventRequest("BALL_IMPACT", 1000L, -5L, "Impact");
        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                timelineEventService.createTimelineEvent(1L, request)
        );
        assertEquals("Duration in milliseconds must be non-negative", ex.getMessage());
    }

    @Test
    void getTimelineEvents_Success_Sorted() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);

        TimelineEvent e1 = new TimelineEvent(mockSession, "BALL_RELEASE", 500L, null, "Release");
        e1.setId(1L);
        TimelineEvent e2 = new TimelineEvent(mockSession, "BALL_PITCH", 1200L, 100L, "Pitch");
        e2.setId(2L);
        TimelineEvent e3 = new TimelineEvent(mockSession, "BALL_IMPACT", 1800L, null, "Impact");
        e3.setId(3L);

        when(timelineEventRepository.findBySessionIdOrderByTimestamp(1L)).thenReturn(List.of(e1, e2, e3));

        List<TimelineEventResponse> events = timelineEventService.getTimelineEvents(1L);

        assertEquals(3, events.size());
        assertEquals("BALL_RELEASE", events.get(0).getEventType());
        assertEquals(500L, events.get(0).getTimestampMs());
        assertEquals("BALL_PITCH", events.get(1).getEventType());
        assertEquals(1200L, events.get(1).getTimestampMs());
        assertEquals("BALL_IMPACT", events.get(2).getEventType());
        assertEquals(1800L, events.get(2).getTimestampMs());
    }

    @Test
    void getTimelineEvents_MissingSession_ThrowsNotFound() {
        when(drsSessionRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                timelineEventService.getTimelineEvents(999L)
        );
    }

    @Test
    void getTimelineEvent_Success() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);

        TimelineEvent event = new TimelineEvent(mockSession, "WICKET_HIT", 2500L, 50L, "Bails dislodged");
        event.setId(5L);

        when(timelineEventRepository.findById(5L)).thenReturn(Optional.of(event));

        TimelineEventResponse response = timelineEventService.getTimelineEvent(1L, 5L);

        assertNotNull(response);
        assertEquals(5L, response.getId());
        assertEquals("WICKET_HIT", response.getEventType());
        assertEquals(2500L, response.getTimestampMs());
    }

    @Test
    void getTimelineEvent_OwnershipMismatch_ThrowsNotFound() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);

        DrsSession otherSession = new DrsSession();
        otherSession.setId(2L);

        TimelineEvent otherEvent = new TimelineEvent(otherSession, "BALL_IMPACT", 1200L, null, "Different session event");
        otherEvent.setId(5L);

        when(timelineEventRepository.findById(5L)).thenReturn(Optional.of(otherEvent));

        assertThrows(ResourceNotFoundException.class, () ->
                timelineEventService.getTimelineEvent(1L, 5L)
        );
    }

    @Test
    void getTimelineEvent_MissingEvent_ThrowsNotFound() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);
        when(timelineEventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                timelineEventService.getTimelineEvent(1L, 999L)
        );
    }
}
