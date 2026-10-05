package com.instantdrs.backend.service;

import com.instantdrs.backend.entity.DrsSession;
import com.instantdrs.backend.repository.DrsSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DrsSessionService {

    private final DrsSessionRepository drsSessionRepository;

    @Value("${instantdrs.storage.original-dir}")
    private String originalDir;

    @Value("${instantdrs.storage.processed-dir}")
    private String processedDir;

    @Value("${instantdrs.video-engine.python}")
    private String pythonExecutable;

    @Value("${instantdrs.video-engine.pipeline}")
    private String pipelineScript;

    @Value("${instantdrs.video-engine.fast-pipeline:${instantdrs.video-engine.pipeline}}")
    private String fastPipelineScript;

    @Value("${instantdrs.video-engine.use-fast-pipeline:true}")
    private boolean useFastPipeline;

    public DrsSessionService(DrsSessionRepository drsSessionRepository) {
        this.drsSessionRepository = drsSessionRepository;
    }

    public DrsSession createSession(DrsSession session) {
        if (session.getStatus() == null) {
            session.setStatus("CREATED");
        }
        return drsSessionRepository.save(session);
    }

    public DrsSession getSessionById(Long id) {
        return drsSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("DrsSession not found with id: " + id));
    }

    public List<DrsSession> getAllSessions() {
        return drsSessionRepository.findAll();
    }

    public ResponseEntity<Map<String, Object>> uploadVideo(Long id, MultipartFile video) {
        Map<String, Object> response = new HashMap<>();

        try {
            DrsSession session = getSessionById(id);

            File dir = new File(originalDir);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = "session_" + id + "_original.mp4";

            Path filepath = Paths.get(originalDir, filename)
                    .toAbsolutePath()
                    .normalize();

            video.transferTo(filepath.toFile());

            session.setOriginalVideoPath(filepath.toString());
            drsSessionRepository.save(session);

            response.put("sessionId", id);
            response.put("message", "Video uploaded successfully");
            response.put("status", session.getStatus());

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            response.put("sessionId", id);
            response.put("message", "Video upload failed: " + e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> processVideo(Long id) {

        Map<String, Object> response = new HashMap<>();

        try {

            DrsSession session = getSessionById(id);

            // Check whether original video exists
            if (session.getOriginalVideoPath() == null
                    || !new File(session.getOriginalVideoPath()).exists()) {

                response.put("sessionId", id);
                response.put("message", "Original video missing");
                response.put("status", "FAILED");

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            // Update session status
            session.setStatus("PROCESSING");
            drsSessionRepository.save(session);

            // Create processed video directory
            File pDir = new File(processedDir);

            if (!pDir.exists()) {
                pDir.mkdirs();
            }

            // Output file
            String outputFilename = "session_" + id + "_processed.mp4";

            Path outputPath = Paths.get(processedDir, outputFilename)
                    .toAbsolutePath()
                    .normalize();

            // Select fast pipeline if enabled, fallback to legacy pipeline
            String scriptToRun = (useFastPipeline && fastPipelineScript != null && !fastPipelineScript.isBlank() && new File(fastPipelineScript).exists())
                    ? fastPipelineScript
                    : pipelineScript;

            // Create Python pipeline process
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonExecutable,
                    scriptToRun,
                    "--sport",
                    session.getGame(),
                    "--input",
                    session.getOriginalVideoPath(),
                    "--output",
                    outputPath.toString()
            );

            // Combine stdout and stderr
            processBuilder.redirectErrorStream(true);

            // NEW:
            // Show Python pipeline output directly
            // inside the IntelliJ console.
            processBuilder.inheritIO();

            // Start pipeline
            Process process = processBuilder.start();

            // Wait until pipeline finishes
            int exitCode = process.waitFor();

            // Check result
            if (exitCode == 0 && outputPath.toFile().exists()) {

                session.setProcessedVideoPath(outputPath.toString());
                session.setStatus("COMPLETED");
                session.setCompletedAt(LocalDateTime.now());

                drsSessionRepository.save(session);

                response.put("sessionId", id);
                response.put("message", "Video processing completed");
                response.put("status", "COMPLETED");
                response.put(
                        "processedVideoPath",
                        session.getProcessedVideoPath()
                );

                return ResponseEntity.ok(response);

            } else {

                session.setStatus("FAILED");
                drsSessionRepository.save(session);

                response.put("sessionId", id);
                response.put(
                        "message",
                        "Video processing failed with exit code: " + exitCode
                );
                response.put("status", "FAILED");

                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(response);
            }

        } catch (Exception e) {

            response.put("sessionId", id);
            response.put(
                    "message",
                    "Video processing error: " + e.getMessage()
            );
            response.put("status", "FAILED");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }

    public ResponseEntity<Resource> downloadVideo(Long id) {

        try {

            DrsSession session = getSessionById(id);

            if (!"COMPLETED".equals(session.getStatus())
                    || session.getProcessedVideoPath() == null) {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .build();
            }

            Path path = Paths.get(session.getProcessedVideoPath());

            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() || resource.isReadable()) {

                return ResponseEntity.ok()
                        .contentType(
                                MediaType.parseMediaType("video/mp4")
                        )
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\""
                                        + resource.getFilename()
                                        + "\""
                        )
                        .body(resource);

            } else {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .build();
            }

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}
