package com.instantdrs.backend.repository;

import com.instantdrs.backend.entity.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    @Query("SELECT t FROM TimelineEvent t WHERE t.session.id = :sessionId ORDER BY t.timestampMs ASC, t.id ASC")
    List<TimelineEvent> findBySessionIdOrderByTimestamp(@Param("sessionId") Long sessionId);

    @Query("SELECT t FROM TimelineEvent t WHERE t.id = :id AND t.session.id = :sessionId")
    Optional<TimelineEvent> findByIdAndSessionId(@Param("id") Long id, @Param("sessionId") Long sessionId);
}
