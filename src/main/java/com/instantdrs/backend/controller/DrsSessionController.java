package com.instantdrs.backend.controller;

import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.service.DrsSessionService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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

    @PostMapping("/{id}/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @PathVariable Long id,
            @RequestParam("video") MultipartFile video) {
        return drsSessionService.uploadVideo(id, video);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Map<String, Object>> processVideo(@PathVariable Long id) {
        return drsSessionService.processVideo(id);
    }

    @GetMapping("/{id}/video")
    public ResponseEntity<Resource> downloadVideo(@PathVariable Long id) {
        return drsSessionService.downloadVideo(id);
    }
}
