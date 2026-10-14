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
class ReviewDecisionServiceTest {

    @Mock
    private ReviewDecisionRepository reviewDecisionRepository;

    @Mock
    private DrsSessionRepository drsSessionRepository;

    @Mock
    private TimelineEventRepository timelineEventRepository;

    private ReviewDecisionService reviewDecisionService;

    private DrsSession mockSession;

    @BeforeEach
    void setUp() {
        reviewDecisionService = new ReviewDecisionService(reviewDecisionRepository, drsSessionRepository, timelineEventRepository);

        mockSession = new DrsSession();
        mockSession.setId(1L);
        mockSession.setGame("cricket");
        mockSession.setStatus("COMPLETED");
    }

    @Test
    void saveDecision_Success_WithoutSelectedEvent() {
        ReviewDecisionRequest request = new ReviewDecisionRequest("OUT", null, "Pad first, inline wicket", "umpire_john", LocalDateTime.now());

        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));
        when(reviewDecisionRepository.save(any(ReviewDecision.class))).thenAnswer(invocation -> {
            ReviewDecision decision = invocation.getArgument(0);
            decision.setId(100L);
            decision.setCreatedAt(LocalDateTime.now());
            return decision;
        });

        ReviewDecisionResponse response = reviewDecisionService.saveDecision(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getSessionId());
        assertEquals("OUT", response.getDecision());
        assertNull(response.getSelectedEventId());
        assertEquals("Pad first, inline wicket", response.getNotes());
        assertEquals("umpire_john", response.getReviewedBy());
        assertNotNull(response.getReviewedAt());

        // Verify session was updated with user's selected decision
        assertEquals("OUT", mockSession.getDecision());
        verify(drsSessionRepository).save(mockSession);
    }

    @Test
    void saveDecision_Success_WithValidSelectedEvent() {
        TimelineEvent mockEvent = new TimelineEvent(mockSession, "BALL_IMPACT", 1500L, null, "Impact on pad");
        mockEvent.setId(25L);

        ReviewDecisionRequest request = new ReviewDecisionRequest("NOT_OUT", 25L, "Missing leg stump", "umpire_mike", LocalDateTime.now());

        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));
        when(timelineEventRepository.findById(25L)).thenReturn(Optional.of(mockEvent));
        when(reviewDecisionRepository.save(any(ReviewDecision.class))).thenAnswer(invocation -> {
            ReviewDecision decision = invocation.getArgument(0);
            decision.setId(101L);
            decision.setCreatedAt(LocalDateTime.now());
            return decision;
        });

        ReviewDecisionResponse response = reviewDecisionService.saveDecision(1L, request);

        assertNotNull(response);
        assertEquals("NOT_OUT", response.getDecision());
        assertEquals(25L, response.getSelectedEventId());
        assertEquals("Missing leg stump", response.getNotes());
        assertEquals("NOT_OUT", mockSession.getDecision());
    }

    @Test
    void saveDecision_SelectedEventBelongsToDifferentSession_ThrowsBadRequest() {
        DrsSession otherSession = new DrsSession();
        otherSession.setId(99L);

        TimelineEvent otherEvent = new TimelineEvent(otherSession, "BALL_IMPACT", 1500L, null, "Other session event");
        otherEvent.setId(25L);

        ReviewDecisionRequest request = new ReviewDecisionRequest("OUT", 25L, "Notes", "umpire", null);

        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));
        when(timelineEventRepository.findById(25L)).thenReturn(Optional.of(otherEvent));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reviewDecisionService.saveDecision(1L, request)
        );
        assertTrue(ex.getMessage().contains("does not belong to session"));
        verify(reviewDecisionRepository, never()).save(any());
    }

    @Test
    void saveDecision_MissingSession_ThrowsNotFound() {
        ReviewDecisionRequest request = new ReviewDecisionRequest("OUT", null, "Notes", "umpire", null);
        when(drsSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                reviewDecisionService.saveDecision(999L, request)
        );
        verify(reviewDecisionRepository, never()).save(any());
    }

    @Test
    void saveDecision_BlankDecision_ThrowsBadRequest() {
        ReviewDecisionRequest request = new ReviewDecisionRequest("   ", null, "Notes", "umpire", null);
        when(drsSessionRepository.findById(1L)).thenReturn(Optional.of(mockSession));

        assertThrows(IllegalArgumentException.class, () ->
                reviewDecisionService.saveDecision(1L, request)
        );
    }

    @Test
    void getLatestDecision_Success() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);

        ReviewDecision decision = new ReviewDecision(mockSession, "OVERTURNED", null, "Clear edge on ultra-edge", "tv_umpire", LocalDateTime.now());
        decision.setId(50L);

        when(reviewDecisionRepository.findLatestBySessionId(1L)).thenReturn(Optional.of(decision));

        ReviewDecisionResponse response = reviewDecisionService.getLatestDecision(1L);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals("OVERTURNED", response.getDecision());
        assertEquals("Clear edge on ultra-edge", response.getNotes());
    }

    @Test
    void getLatestDecision_MissingSession_ThrowsNotFound() {
        when(drsSessionRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                reviewDecisionService.getLatestDecision(999L)
        );
    }

    @Test
    void getLatestDecision_NoDecisionRecorded_ThrowsNotFound() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);
        when(reviewDecisionRepository.findLatestBySessionId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                reviewDecisionService.getLatestDecision(1L)
        );
    }

    @Test
    void getAllDecisionsForSession_Success() {
        when(drsSessionRepository.existsById(1L)).thenReturn(true);

        ReviewDecision d1 = new ReviewDecision(mockSession, "CONFIRMED", null, "First review", "umpire1", LocalDateTime.now().minusMinutes(5));
        d1.setId(1L);
        ReviewDecision d2 = new ReviewDecision(mockSession, "OVERTURNED", null, "Second review", "umpire2", LocalDateTime.now());
        d2.setId(2L);

        when(reviewDecisionRepository.findBySessionIdOrderByReviewedAtDesc(1L)).thenReturn(List.of(d2, d1));

        List<ReviewDecisionResponse> responses = reviewDecisionService.getAllDecisionsForSession(1L);

        assertEquals(2, responses.size());
        assertEquals("OVERTURNED", responses.get(0).getDecision());
        assertEquals("CONFIRMED", responses.get(1).getDecision());
    }
}
