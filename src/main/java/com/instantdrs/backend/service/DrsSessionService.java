package com.instantdrs.backend.service;

import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.repository.DrsSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DrsSessionService {

    private final DrsSessionRepository drsSessionRepository;

    public DrsSessionService(DrsSessionRepository drsSessionRepository) {
        this.drsSessionRepository = drsSessionRepository;
    }

    public DrsSession createSession(DrsSession session) {
        return drsSessionRepository.save(session);
    }

    public DrsSession getSessionById(Long id) {
        return drsSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("DrsSession not found with id: " + id));
    }

    public List<DrsSession> getAllSessions() {
        return drsSessionRepository.findAll();
    }
}
