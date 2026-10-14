package com.instantdrs.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventRequest {

    @NotBlank(message = "Event type cannot be blank")
    @JsonAlias({"type"})
    private String eventType;

    @NotNull(message = "Timestamp in milliseconds is required")
    @Min(value = 0, message = "Timestamp in milliseconds must be non-negative")
    @JsonAlias({"timestamp", "eventTimestampMs"})
    private Long timestampMs;

    @Min(value = 0, message = "Duration in milliseconds must be non-negative")
    @JsonAlias({"duration", "eventDurationMs"})
    private Long durationMs;

    private String description;
}
