package com.instantdrs.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.instantdrs.backend.entity.TimelineEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventResponse {

    private Long id;
    private Long sessionId;
    private String eventType;
    private Long timestampMs;
    private Long durationMs;
    private String description;
    private LocalDateTime createdAt;

    @JsonProperty("type")
    public String getType() {
        return eventType;
    }

    @JsonProperty("timestamp")
    public Long getTimestamp() {
        return timestampMs;
    }

    @JsonProperty("duration")
    public Long getDuration() {
        return durationMs;
    }

    public static TimelineEventResponse fromEntity(TimelineEvent event) {
        if (event == null) {
            return null;
        }
        return new TimelineEventResponse(
                event.getId(),
                event.getSessionId(),
                event.getEventType(),
                event.getTimestampMs(),
                event.getDurationMs(),
                event.getDescription(),
                event.getCreatedAt()
        );
    }
}
