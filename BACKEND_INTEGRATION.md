# InstantDRS Backend 2.0 Integration with Video Engine, Replay, Timeline & Decision Review

This document explains how the Spring Boot Backend connects with the Python Video Engine to process sports videos, streams replay videos, manages persistent timeline events, and records user review decisions.

---

## Architecture & Flow
The backend functions as the orchestrator for the DRS review workflow:
1. **Client** creates a `DrsSession` specifying the sport.
2. **Client** uploads the original video. The backend saves this locally in `storage/original/`.
3. **Client** triggers the processing API (`POST /api/drs/sessions/{id}/process`).
4. **Backend** starts the Python sequential video pipeline via `ProcessBuilder`.
5. **Python Pipeline** runs the video through Slow Motion, RIFE FPS Interpolation, and Ball Tracking.
6. **Backend** updates session status to `COMPLETED` (or `FAILED`) with `processedVideoPath`.
7. **Client** accesses Replay & Metadata:
   - Retrieves session metadata and processing status via `GET /api/drs/sessions/{id}`.
   - Streams the real processed replay video via `GET /api/drs/sessions/{id}/video`.
8. **Client** manages Timeline Events:
   - Posts key match timestamps (e.g., ball pitch, impact, boundary touch) via `POST /api/drs/sessions/{id}/timeline`.
   - Retrieves sorted timeline events via `GET /api/drs/sessions/{id}/timeline`.
9. **Client** records User Review Decision:
   - Persists manual umpire/referee decision (e.g. OUT, NOT OUT, IN, OVERTURNED) linked to the session and optional timeline event via `POST /api/drs/sessions/{id}/decision`.
   - Retrieves the recorded review decision via `GET /api/drs/sessions/{id}/decision`.

---

## Configuration Properties
Defined in `application.properties`:
```properties
# Python environment executable
instantdrs.video-engine.python=C:/Users/Shivansh Tiwari/Desktop/InstantDRS/video-engine/tool3-sports-ball-tracking/.venv/Scripts/python.exe

# Video pipeline scripts
instantdrs.video-engine.pipeline=C:/Users/Shivansh Tiwari/Desktop/InstantDRS/video-engine/pipeline/run_pipeline.py
instantdrs.video-engine.fast-pipeline=C:/Users/Shivansh Tiwari/Desktop/InstantDRS/video-engine/pipeline/fast_drs_pipeline.py
instantdrs.video-engine.use-fast-pipeline=true

# Storage directories
instantdrs.storage.original-dir=storage/original/
instantdrs.storage.processed-dir=storage/processed/

# Database (Hibernate DDL update preserves existing data)
spring.jpa.hibernate.ddl-auto=update
```

---

## Database Schemas

### 1. `drs_sessions`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK AI | Unique session identifier |
| `game` | VARCHAR(255) | Sport name (cricket, tennis, volleyball, etc.) |
| `status` | VARCHAR(255) | Status (`CREATED`, `PROCESSING`, `COMPLETED`, `FAILED`) |
| `original_video_path` | VARCHAR(255) | Absolute/relative path to uploaded video |
| `processed_video_path` | VARCHAR(255) | Path to processed replay video |
| `decision` | VARCHAR(255) | Latest user review decision |
| `created_at` | DATETIME | Session creation timestamp |
| `completed_at` | DATETIME | Processing completion timestamp |

### 2. `timeline_events`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT PK AI | PRIMARY KEY | Unique event identifier |
| `session_id` | BIGINT | NOT NULL, FK -> `drs_sessions.id` | Associated DRS session |
| `event_type` | VARCHAR(255) | NOT NULL | Type (e.g. `BALL_PITCH`, `BALL_IMPACT`, `WICKET_HIT`) |
| `timestamp_ms` | BIGINT | NOT NULL | Video timestamp in milliseconds |
| `duration_ms` | BIGINT | NULL | Optional event duration in milliseconds |
| `description` | VARCHAR(1000) | NULL | Optional description or commentary |
| `created_at` | DATETIME | NOT NULL | Timestamp when event was recorded |

### 3. `review_decisions`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT PK AI | PRIMARY KEY | Unique review decision identifier |
| `session_id` | BIGINT | NOT NULL, FK -> `drs_sessions.id` | Associated DRS session |
| `decision` | VARCHAR(255) | NOT NULL | User-selected decision (e.g. `OUT`, `NOT_OUT`, `IN`) |
| `selected_event_id` | BIGINT | NULL | Optional timeline event ID tied to decision |
| `notes` | VARCHAR(2000) | NULL | Review notes or reason |
| `reviewed_by` | VARCHAR(255) | NULL | Umpire / reviewer identifier |
| `reviewed_at` | DATETIME | NOT NULL | Review timestamp |
| `created_at` | DATETIME | NOT NULL | Persistence creation timestamp |

---

## API Reference

### 1. Replay & Video Streaming API

#### Retrieve Session Status & Replay Metadata
* **GET** `/api/drs/sessions/{id}`
* **Response (200 OK):**
```json
{
  "id": 1,
  "game": "cricket",
  "status": "COMPLETED",
  "originalVideoPath": "storage/original/session_1_original.mp4",
  "processedVideoPath": "storage/processed/session_1_processed.mp4",
  "decision": "OUT",
  "createdAt": "2026-10-08T18:00:00",
  "completedAt": "2026-10-08T18:01:30"
}
```
* **Error (404 Not Found):** If session does not exist.
```json
{
  "timestamp": "2026-10-08T18:05:00",
  "status": 404,
  "error": "Not Found",
  "message": "DrsSession not found with id: 999"
}
```

#### Stream Replay Video
* **GET** `/api/drs/sessions/{id}/video`
* **Response (200 OK):**
  - Content-Type: `video/mp4`
  - Accept-Ranges: `bytes`
  - Content-Disposition: `inline; filename="session_1_processed.mp4"`
  - Content-Length: `<file byte count>`
  - Body: Binary video stream.
* **Error Handling:**
  - Returns `404 Not Found` if session ID does not exist.
  - Returns `404 Not Found` if session is not `COMPLETED`.
  - Returns `404 Not Found` if processed video file does not exist on disk.
  - No fake videos or placeholder URLs are returned.

---

### 2. Timeline Events API

#### List Timeline Events
* **GET** `/api/drs/sessions/{id}/timeline`
* **Behavior:** Returns persisted events for that session, sorted ascending by `timestampMs`.
* **Response (200 OK):**
```json
[
  {
    "id": 1,
    "sessionId": 1,
    "eventType": "BALL_RELEASE",
    "type": "BALL_RELEASE",
    "timestampMs": 500,
    "timestamp": 500,
    "durationMs": null,
    "duration": null,
    "description": "Bowler releases ball",
    "createdAt": "2026-10-08T18:02:00"
  },
  {
    "id": 2,
    "sessionId": 1,
    "eventType": "BALL_PITCH",
    "type": "BALL_PITCH",
    "timestampMs": 1400,
    "timestamp": 1400,
    "durationMs": 100,
    "duration": 100,
    "description": "Pitched in line with stumps",
    "createdAt": "2026-10-08T18:02:05"
  }
]
```
* **Error (404 Not Found):** If session does not exist.

#### Create Timeline Event
* **POST** `/api/drs/sessions/{id}/timeline`
* **Request Body:**
```json
{
  "eventType": "BALL_IMPACT",
  "timestampMs": 1850,
  "durationMs": 50,
  "description": "Impact on front pad"
}
```
*(Accepts aliases `"type"` for `"eventType"`, `"timestamp"` for `"timestampMs"`, `"duration"` for `"durationMs"`).*
* **Response (201 Created):**
```json
{
  "id": 3,
  "sessionId": 1,
  "eventType": "BALL_IMPACT",
  "type": "BALL_IMPACT",
  "timestampMs": 1850,
  "timestamp": 1850,
  "durationMs": 50,
  "duration": 50,
  "description": "Impact on front pad",
  "createdAt": "2026-10-08T18:02:10"
}
```
* **Validation & Errors:**
  - `400 Bad Request` if `eventType` is blank.
  - `400 Bad Request` if `timestampMs` is missing or negative.
  - `400 Bad Request` if `durationMs` is negative.
  - `404 Not Found` if session ID does not exist.

#### Get Single Timeline Event
* **GET** `/api/drs/sessions/{id}/timeline/{eventId}`
* **Response (200 OK):**
```json
{
  "id": 3,
  "sessionId": 1,
  "eventType": "BALL_IMPACT",
  "type": "BALL_IMPACT",
  "timestampMs": 1850,
  "timestamp": 1850,
  "durationMs": 50,
  "duration": 50,
  "description": "Impact on front pad",
  "createdAt": "2026-10-08T18:02:10"
}
```
* **Validation & Errors:**
  - `404 Not Found` if event does not exist or does not belong to session `{id}`.
  - `404 Not Found` if session does not exist.

---

### 3. User Review Decision API

#### Save User Review Decision
* **POST** `/api/drs/sessions/{id}/decision` (alias: `/api/drs/sessions/{id}/review-decision`)
* **Request Body:**
```json
{
  "decision": "OUT",
  "selectedEventId": 3,
  "notes": "Impact in line, wickets hitting (Umpire's call)",
  "reviewedBy": "third_umpire_richard",
  "reviewedAt": "2026-10-08T18:03:00"
}
```
*(Accepts aliases: `"reviewDecision"` / `"userDecision"` for `"decision"`, `"eventId"` for `"selectedEventId"`, `"timestamp"` for `"reviewedAt"`).*
* **Response (201 Created):**
```json
{
  "id": 1,
  "sessionId": 1,
  "decision": "OUT",
  "reviewDecision": "OUT",
  "selectedEventId": 3,
  "notes": "Impact in line, wickets hitting (Umpire's call)",
  "reviewedBy": "third_umpire_richard",
  "reviewedAt": "2026-10-08T18:03:00",
  "createdAt": "2026-10-08T18:03:01"
}
```
* **Behavior:**
  - Persists the review decision audit record.
  - Updates `DrsSession.decision` so replay metadata reflects the user decision.
  - **Never calculates or auto-assigns the final decision.** Only the user's selected decision is saved.
* **Validation & Errors:**
  - `400 Bad Request` if `decision` is blank.
  - `400 Bad Request` if `selectedEventId` is provided but belongs to another session or does not exist.
  - `404 Not Found` if session ID does not exist.

#### Get Latest Decision
* **GET** `/api/drs/sessions/{id}/decision` (alias: `/api/drs/sessions/{id}/review-decision`)
* **Response (200 OK):**
```json
{
  "id": 1,
  "sessionId": 1,
  "decision": "OUT",
  "reviewDecision": "OUT",
  "selectedEventId": 3,
  "notes": "Impact in line, wickets hitting (Umpire's call)",
  "reviewedBy": "third_umpire_richard",
  "reviewedAt": "2026-10-08T18:03:00",
  "createdAt": "2026-10-08T18:03:01"
}
```
* **Error (404 Not Found):** If session does not exist or no review decision has been recorded yet.

#### Get All Decisions Audit Trail
* **GET** `/api/drs/sessions/{id}/decisions`
* **Response (200 OK):** Array of all decisions submitted for this session, sorted newest to oldest.

---

## Testing Commands & Results

### 1. Run Complete Test Suite
```powershell
.\mvnw.cmd clean test
```
**Test Results:**
- Tests run: **55**
- Failures: **0**
- Errors: **0**
- Skipped: **0**
- Status: **BUILD SUCCESS**

### 2. Manual E2E Testing (PowerShell)
```powershell
# 1. Create Session
$session = Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions" -Method Post -ContentType "application/json" -Body '{"game": "cricket", "status": "CREATED"}'
$id = $session.id

# 2. Upload Video
curl.exe -s -X POST "http://localhost:8080/api/drs/sessions/$id/video" -F "video=@test.mp4"

# 3. Process Video
Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$id/process" -Method Post

# 4. Stream / Download Replay Video
curl.exe -s -i -X GET "http://localhost:8080/api/drs/sessions/$id/video" -o "replay.mp4"

# 5. Add Timeline Events
Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$id/timeline" -Method Post -ContentType "application/json" -Body '{"eventType": "BALL_PITCH", "timestampMs": 1400, "description": "Pitched on middle"}'
Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$id/timeline" -Method Post -ContentType "application/json" -Body '{"eventType": "BALL_IMPACT", "timestampMs": 1900, "description": "Pad impact"}'

# 6. Retrieve Sorted Timeline
Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$id/timeline" -Method Get

# 7. Record User Review Decision
Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$id/decision" -Method Post -ContentType "application/json" -Body '{"decision": "OUT", "selectedEventId": 2, "notes": "Hitting stumps"}'

# 8. Retrieve Replay Metadata and Decision
Invoke-RestMethod -Uri "http://localhost:8080/api/drs/sessions/$id" -Method Get
```

---

## Summary of Changes & Architecture

| Component | Files Changed / Added | Role |
|---|---|---|
| **Entities** | `entity/TimelineEvent.java`<br>`entity/ReviewDecision.java`<br>`entity/DrsSession.java` | Persistent MySQL models for timeline events and user review decisions. |
| **DTOs** | `dto/TimelineEventRequest.java`<br>`dto/TimelineEventResponse.java`<br>`dto/ReviewDecisionRequest.java`<br>`dto/ReviewDecisionResponse.java` | Validated request bodies with Jackson aliases and response representations. |
| **Repositories** | `repository/TimelineEventRepository.java`<br>`repository/ReviewDecisionRepository.java` | Spring Data JPA repositories with custom query methods for session queries and sorting. |
| **Services** | `service/TimelineEventService.java`<br>`service/ReviewDecisionService.java`<br>`service/DrsSessionService.java` | Ownership validation, sorting, error handling, video streaming headers, and decision persistence. |
| **Controllers** | `controller/TimelineEventController.java`<br>`controller/ReviewDecisionController.java`<br>`controller/DrsSessionController.java` | REST endpoints for Replay, Timeline, and User Review Decision. |
| **Exceptions** | `exception/ResourceNotFoundException.java`<br>`exception/GlobalExceptionHandler.java` | Standardized HTTP 404, 400, and 500 error responses across all APIs. |
| **Tests** | `service/TimelineEventServiceTest.java`<br>`controller/TimelineEventControllerTest.java`<br>`service/ReviewDecisionServiceTest.java`<br>`controller/ReviewDecisionControllerTest.java`<br>`controller/DrsSessionReplayTest.java` | Comprehensive unit and MockMvc test coverage. |

### Limitations & Assumptions
- **Storage:** Videos are stored on the local filesystem (`storage/processed/`) as defined in `application.properties`. Cloud object storage (e.g. AWS S3) is not currently enabled.
- **Decision Autonomy:** The backend does not calculate, predict, or auto-assign match decisions (IN/OUT); decisions are exclusively user-selected and persisted via the decision API.
