package com.instantdrs.backend.repository;

import com.instantdrs.backend.entity.DrsSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrsSessionRepository extends JpaRepository<DrsSession, Long> {
}
