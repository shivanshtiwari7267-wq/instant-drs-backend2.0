package com.instantdrs.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_decisions")
@Getter
@Setter
@NoArgsConstructor
public class ReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private DrsSession session;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "selected_event_id")
    private Long selectedEventId;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReviewDecision(DrsSession session, String decision, Long selectedEventId, String notes, String reviewedBy, LocalDateTime reviewedAt) {
        this.session = session;
        this.decision = decision;
        this.selectedEventId = selectedEventId;
        this.notes = notes;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt != null ? reviewedAt : LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.reviewedAt == null) {
            this.reviewedAt = LocalDateTime.now();
        }
    }

    public Long getSessionId() {
        return session != null ? session.getId() : null;
    }
}
