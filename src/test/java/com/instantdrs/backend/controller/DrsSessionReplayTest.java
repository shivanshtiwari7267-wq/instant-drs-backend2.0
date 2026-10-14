package com.instantdrs.backend.controller;

import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.exception.GlobalExceptionHandler;
import com.instantdrs.backend.exception.ResourceNotFoundException;
import com.instantdrs.backend.repository.DrsSessionRepository;
import com.instantdrs.backend.service.DrsSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DrsSessionReplayTest {

    @Mock
    private DrsSessionRepository drsSessionRepository;

    private DrsSessionService drsSessionService;
    private DrsSessionController drsSessionController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        drsSessionService = new DrsSessionService(drsSessionRepository);
        drsSessionController = new DrsSessionController(drsSessionService);
        mockMvc = MockMvcBuilders.standaloneSetup(drsSessionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSessionById_ReplayMetadata_Success() throws Exception {
        DrsSession session = new DrsSession();
        session.setId(42L);
        session.setGame("cricket");
        session.setStatus("COMPLETED");
        session.setProcessedVideoPath("storage/processed/session_42_processed.mp4");
        session.setDecision("OUT");
        session.setCreatedAt(LocalDateTime.now());
        session.setCompletedAt(LocalDateTime.now());

        when(drsSessionRepository.findById(42L)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/drs/sessions/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42L))
                .andExpect(jsonPath("$.game").value("cricket"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decision").value("OUT"))
                .andExpect(jsonPath("$.processedVideoPath").value("storage/processed/session_42_processed.mp4"));
    }

    @Test
    void getSessionById_MissingSession_ReturnsNotFound() throws Exception {
        when(drsSessionRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/drs/sessions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void downloadVideo_MissingSession_ReturnsNotFound() throws Exception {
        when(drsSessionRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/drs/sessions/999/video"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadVideo_SessionNotCompleted_ReturnsNotFound() throws Exception {
        DrsSession session = new DrsSession();
        session.setId(10L);
        session.setStatus("PROCESSING");
        session.setProcessedVideoPath(null);

        when(drsSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/drs/sessions/10/video"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadVideo_VideoFileDoesNotExist_ReturnsNotFound() throws Exception {
        DrsSession session = new DrsSession();
        session.setId(10L);
        session.setStatus("COMPLETED");
        session.setProcessedVideoPath("non_existent_folder/missing_video.mp4");

        when(drsSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/drs/sessions/10/video"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadVideo_Success_StreamsRealProcessedFile(@TempDir Path tempDir) throws Exception {
        File videoFile = tempDir.resolve("session_10_processed.mp4").toFile();
        try (FileOutputStream fos = new FileOutputStream(videoFile)) {
            fos.write(new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}); // fake mp4 header bytes
        }

        DrsSession session = new DrsSession();
        session.setId(10L);
        session.setStatus("COMPLETED");
        session.setProcessedVideoPath(videoFile.getAbsolutePath());

        when(drsSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/drs/sessions/10/video"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"" + videoFile.getName() + "\""))
                .andExpect(header().longValue("Content-Length", videoFile.length()));
    }
}
