package com.instantdrs.backend.controller;

import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.service.DrsSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drs/sessions")
public class DrsSessionController {

    private final DrsSessionService drsSessionService;

    public DrsSessionController(DrsSessionService drsSessionService) {
        this.drsSessionService = drsSessionService;
    }

    @PostMapping
    public DrsSession createSession(@RequestBody DrsSession session) {
        return drsSessionService.createSession(session);
    }

    @GetMapping("/{id}")
    public DrsSession getSessionById(@PathVariable Long id) {
        return drsSessionService.getSessionById(id);
    }

    @GetMapping
    public List<DrsSession> getAllSessions() {
        return drsSessionService.getAllSessions();
    }
}
