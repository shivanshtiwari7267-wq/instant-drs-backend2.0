package com.instantdrs.backend.controller;

import com.instantdrs.backend.dto.TimelineEventRequest;
import com.instantdrs.backend.dto.TimelineEventResponse;
import com.instantdrs.backend.exception.GlobalExceptionHandler;
import com.instantdrs.backend.exception.ResourceNotFoundException;
import com.instantdrs.backend.service.TimelineEventService;
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
class TimelineEventControllerTest {

    @Mock
    private TimelineEventService timelineEventService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TimelineEventController controller = new TimelineEventController(timelineEventService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createTimelineEvent_Success() throws Exception {
        TimelineEventResponse response = new TimelineEventResponse(1L, 10L, "BALL_PITCH", 1500L, 200L, "Pitched in line", LocalDateTime.now());

        when(timelineEventService.createTimelineEvent(eq(10L), any(TimelineEventRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "eventType": "BALL_PITCH",
                    "timestampMs": 1500,
                    "durationMs": 200,
                    "description": "Pitched in line"
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.sessionId").value(10L))
                .andExpect(jsonPath("$.eventType").value("BALL_PITCH"))
                .andExpect(jsonPath("$.timestampMs").value(1500L));
    }

    @Test
    void createTimelineEvent_InvalidInput_BlankEventType_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "eventType": "",
                    "timestampMs": 1500
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void createTimelineEvent_InvalidInput_NegativeTimestamp_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "eventType": "BALL_IMPACT",
                    "timestampMs": -100
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/10/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createTimelineEvent_MissingSession_ReturnsNotFound() throws Exception {
        when(timelineEventService.createTimelineEvent(eq(999L), any(TimelineEventRequest.class)))
                .thenThrow(new ResourceNotFoundException("DrsSession not found with id: 999"));

        String json = """
                {
                    "eventType": "BALL_PITCH",
                    "timestampMs": 1500,
                    "durationMs": 200,
                    "description": "Pitched in line"
                }
                """;

        mockMvc.perform(post("/api/drs/sessions/999/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("DrsSession not found with id: 999"));
    }

    @Test
    void getTimelineEvents_Success() throws Exception {
        TimelineEventResponse e1 = new TimelineEventResponse(1L, 10L, "BALL_RELEASE", 500L, null, "Release", LocalDateTime.now());
        TimelineEventResponse e2 = new TimelineEventResponse(2L, 10L, "BALL_PITCH", 1200L, 100L, "Pitch", LocalDateTime.now());

        when(timelineEventService.getTimelineEvents(10L)).thenReturn(List.of(e1, e2));

        mockMvc.perform(get("/api/drs/sessions/10/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("BALL_RELEASE"))
                .andExpect(jsonPath("$[0].timestampMs").value(500L))
                .andExpect(jsonPath("$[1].eventType").value("BALL_PITCH"))
                .andExpect(jsonPath("$[1].timestampMs").value(1200L));
    }

    @Test
    void getTimelineEvents_MissingSession_ReturnsNotFound() throws Exception {
        when(timelineEventService.getTimelineEvents(999L))
                .thenThrow(new ResourceNotFoundException("DrsSession not found with id: 999"));

        mockMvc.perform(get("/api/drs/sessions/999/timeline"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getTimelineEventById_Success() throws Exception {
        TimelineEventResponse event = new TimelineEventResponse(5L, 10L, "WICKET_HIT", 2500L, 50L, "Hit middle stump", LocalDateTime.now());

        when(timelineEventService.getTimelineEvent(10L, 5L)).thenReturn(event);

        mockMvc.perform(get("/api/drs/sessions/10/timeline/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.sessionId").value(10L))
                .andExpect(jsonPath("$.eventType").value("WICKET_HIT"));
    }

    @Test
    void getTimelineEventById_MissingSessionOrEvent_ReturnsNotFound() throws Exception {
        when(timelineEventService.getTimelineEvent(10L, 999L))
                .thenThrow(new ResourceNotFoundException("Timeline event not found with id: 999"));

        mockMvc.perform(get("/api/drs/sessions/10/timeline/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
