package com.instantdrs.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionRequest {

    @NotBlank(message = "Decision cannot be blank")
    @JsonAlias({"reviewDecision", "userDecision"})
    private String decision;

    @JsonAlias({"eventId", "timelineEventId"})
    private Long selectedEventId;

    private String notes;

    private String reviewedBy;

    @JsonAlias({"timestamp", "reviewTimestamp"})
    private LocalDateTime reviewedAt;
}
