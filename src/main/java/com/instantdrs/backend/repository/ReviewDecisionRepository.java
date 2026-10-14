package com.instantdrs.backend.repository;

import com.instantdrs.backend.entity.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {

    @Query("SELECT r FROM ReviewDecision r WHERE r.session.id = :sessionId ORDER BY r.reviewedAt DESC, r.id DESC")
    List<ReviewDecision> findBySessionIdOrderByReviewedAtDesc(@Param("sessionId") Long sessionId);

    @Query("SELECT r FROM ReviewDecision r WHERE r.session.id = :sessionId ORDER BY r.reviewedAt DESC, r.id DESC LIMIT 1")
    Optional<ReviewDecision> findLatestBySessionId(@Param("sessionId") Long sessionId);
}
