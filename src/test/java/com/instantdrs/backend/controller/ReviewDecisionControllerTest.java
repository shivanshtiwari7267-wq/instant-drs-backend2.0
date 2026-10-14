package com.instantdrs.backend.controller;

import com.instantdrs.backend.dto.ReviewDecisionRequest;
import com.instantdrs.backend.dto.ReviewDecisionResponse;
import com.instantdrs.backend.exception.GlobalExceptionHandler;
import com.instantdrs.backend.exception.ResourceNotFoundException;
import com.instantdrs.backend.service.ReviewDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewDecisionControllerTest {

    @Mock
    private ReviewDecisionService reviewDecisionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReviewDecisionController controller = new ReviewDecisionController(reviewDecisionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void saveDecision_Success() throws Exception {
        ReviewDecisionResponse response = new ReviewDecisionResponse(1L, 10L, "OUT", 5L, "Clear edge and caught", "third_umpire", LocalDateTime.now(), LocalDateTime.now());

        when(reviewDecisionService.saveDecision(eq(10L), any(ReviewDecisionRequest.class))).thenReturn(response);

        String json = """
                {
                    "decision": "OUT",
                    "selectedEventId": 5,
                    "notes": "Clear edge and caught",
                    "reviewedBy": "third_umpire"
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.sessionId").value(10L))
                .andExpect(jsonPath("$.decision").value("OUT"))
                .andExpect(jsonPath("$.selectedEventId").value(5L))
                .andExpect(jsonPath("$.notes").value("Clear edge and caught"));
    }

    @Test
    void saveDecision_AliasEndpoint_Success() throws Exception {
        ReviewDecisionResponse response = new ReviewDecisionResponse(2L, 10L, "NOT_OUT", null, "Pitching outside leg", "third_umpire", LocalDateTime.now(), LocalDateTime.now());

        when(reviewDecisionService.saveDecision(eq(10L), any(ReviewDecisionRequest.class))).thenReturn(response);

        String json = """
                {
                    "decision": "NOT_OUT",
                    "notes": "Pitching outside leg",
                    "reviewedBy": "third_umpire"
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/review-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("NOT_OUT"));
    }

    @Test
    void saveDecision_InvalidInput_BlankDecision_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "decision": ""
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void saveDecision_MissingSession_ReturnsNotFound() throws Exception {
        when(reviewDecisionService.saveDecision(eq(999L), any(ReviewDecisionRequest.class)))
                .thenThrow(new ResourceNotFoundException("DrsSession not found with id: 999"));

        String json = """
                {
                    "decision": "OUT"
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/999/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void saveDecision_SelectedEventMismatch_ReturnsBadRequest() throws Exception {
        when(reviewDecisionService.saveDecision(eq(10L), any(ReviewDecisionRequest.class)))
                .thenThrow(new IllegalArgumentException("Selected event with id 88 does not belong to session id 10"));

        String json = """
                {
                    "decision": "OUT",
                    "selectedEventId": 88,
                    "notes": "Notes"
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Selected event with id 88 does not belong to session id 10"));
    }

    @Test
    void getLatestDecision_Success() throws Exception {
        ReviewDecisionResponse response = new ReviewDecisionResponse(1L, 10L, "OUT", 5L, "Impact in line", "umpire", LocalDateTime.now(), LocalDateTime.now());

        when(reviewDecisionService.getLatestDecision(10L)).thenReturn(response);

        mockMvc.perform(get("/api/drs/sessions/10/decision"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.decision").value("OUT"));
    }

    @Test
    void getLatestDecision_MissingSessionOrNoDecision_ReturnsNotFound() throws Exception {
        when(reviewDecisionService.getLatestDecision(999L))
                .thenThrow(new ResourceNotFoundException("No review decision found for session id: 999"));

        mockMvc.perform(get("/api/drs/sessions/999/decision"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAllDecisions_Success() throws Exception {
        ReviewDecisionResponse r1 = new ReviewDecisionResponse(1L, 10L, "OUT", null, "Initial", "umpire", LocalDateTime.now(), LocalDateTime.now());
        ReviewDecisionResponse r2 = new ReviewDecisionResponse(2L, 10L, "NOT_OUT", null, "Review", "tv_umpire", LocalDateTime.now(), LocalDateTime.now());

        when(reviewDecisionService.getAllDecisionsForSession(10L)).thenReturn(List.of(r2, r1));

        mockMvc.perform(get("/api/drs/sessions/10/decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].decision").value("NOT_OUT"))
                .andExpect(jsonPath("$[1].decision").value("OUT"));
    }
}
