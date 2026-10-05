# InstantDRS Backend 2.0 Integration with Video Engine

This document explains how the Spring Boot Backend connects with the Python Video Engine to process sports videos.

## Architecture & Flow
The backend functions as the orchestrator. The overall flow is:
1. **Client** creates a `DrsSession` specifying the sport.
2. **Client** uploads the original video. The backend saves this locally.
3. **Client** triggers the processing API.
4. **Backend** starts the Python sequential video pipeline via `ProcessBuilder`.
5. **Python Pipeline** runs the video through Tool 1 (Slow Motion), Tool 2 (RIFE FPS Interpolation), and Tool 3 (Ball Tracking).
6. **Backend** waits for the pipeline to finish and updates the session status.
7. **Client** retrieves the final processed video.

## Configuration Properties
The backend connects to the Python engine using properties defined in `application.properties`. 
```properties
# Python environment executable (can be absolute path to virtual environment python.exe)
instantdrs.video-engine.python=python

# Path to the main pipeline script
instantdrs.video-engine.pipeline=C:/Users/Shivansh Tiwari/Desktop/InstantDRS/video-engine/pipeline/run_pipeline.py

# Local storage locations for uploaded and processed videos
instantdrs.storage.original-dir=storage/original/
instantdrs.storage.processed-dir=storage/processed/
```

## Storage Locations
Videos are not stored as BLOBs in MySQL. Instead, their paths are stored in the `DrsSession` entity.
- **Original Videos:** Stored in `storage/original/` as `session_{id}_original.mp4`.
- **Processed Videos:** Stored in `storage/processed/` as `session_{id}_processed.mp4`.

Directories are automatically created if they don't exist. Unique filenames prevent collisions.

## How Spring Boot Calls Python
Processing is invoked in `DrsSessionService` using Java's `ProcessBuilder`. We do not use unsafe string concatenation. 
```java
ProcessBuilder processBuilder = new ProcessBuilder(
        pythonExecutable,
        pipelineScript,
        "--sport", session.getGame(),
        "--input", session.getOriginalVideoPath(),
        "--output", outputPath.toString()
);
Process process = processBuilder.start();
int exitCode = process.waitFor();
```
The exit code determines whether the processing is marked as `COMPLETED` or `FAILED`. 

## API Endpoints Added
The following endpoints were added to `DrsSessionController`:

1. **Upload Original Video**
   - **POST** `/api/drs/sessions/{id}/video`
   - **Body:** `multipart/form-data` with parameter `video`
   - **Behavior:** Saves the video, updates `originalVideoPath`, and returns success JSON.

2. **Start Processing**
   - **POST** `/api/drs/sessions/{id}/process`
   - **Behavior:** Verifies the session and file, changes status to `PROCESSING`, launches the Python script, waits for the result, updates `processedVideoPath`, and sets status to `COMPLETED` or `FAILED`.

3. **Retrieve Final Video**
   - **GET** `/api/drs/sessions/{id}/video`
   - **Behavior:** Returns the processed `.mp4` file as a `Resource` attachment if the status is `COMPLETED`.

## Testing Commands
To verify the integration end-to-end:

1. **Compile the backend**
   ```bash
   .\mvnw.cmd clean compile
   ```
2. **Start Spring Boot**
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
3. **Execute E2E Test (PowerShell)**
   ```powershell
   # 1. Download a test video
   Invoke-WebRequest -Uri "https://www.w3schools.com/html/mov_bbb.mp4" -OutFile "test.mp4"
   
   # 2. Create Session
   $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions" -Method Post -ContentType "application/json" -Body '{"game": "volleyball", "status": "CREATED"}'
   
   # 3. Upload Video
   curl.exe -s -X POST "http://localhost:8080/api/drs/sessions/$($resp.id)/video" -F "video=@test.mp4"
   
   # 4. Process Video (Wait for this to finish)
   Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$($resp.id)/process" -Method Post
   
   # 5. Download Final Video
   curl.exe -s -X GET "http://localhost:8080/api/drs/sessions/$($resp.id)/video" -o "final_downloaded.mp4"
   ```
