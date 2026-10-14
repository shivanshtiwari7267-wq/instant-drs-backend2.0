package com.instantdrs.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.instantdrs.backend.entity.ReviewDecision;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionResponse {

    private Long id;
    private Long sessionId;
    private String decision;
    private Long selectedEventId;
    private String notes;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    @JsonProperty("reviewDecision")
    public String getReviewDecision() {
        return decision;
    }

    public static ReviewDecisionResponse fromEntity(ReviewDecision entity) {
        if (entity == null) {
            return null;
        }
        return new ReviewDecisionResponse(
                entity.getId(),
                entity.getSessionId(),
                entity.getDecision(),
                entity.getSelectedEventId(),
                entity.getNotes(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getCreatedAt()
        );
    }
}
