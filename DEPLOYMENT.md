# InstantDRS Backend — Production Deployment & Docker Guide

This guide provides complete instructions for building and deploying the unified InstantDRS container (Spring Boot + Python OpenVINO video engine + FFmpeg) to production, enabling communication with the Android release APK across cellular networks and Wi-Fi.

---

## 1. Production Architecture

InstantDRS combines a Java 21 Spring Boot REST orchestrator with a high-performance Python 3 OpenVINO sports review pipeline in a single container.

```
                           +--------------------------------------+
                           |   InstantDRS Android Release APK     |
                           |   (Kotlin + Retrofit + OkHttp)       |
                           +--------------------------------------+
                                     |                    |
            (Optional Gateway for    |                    |  (Direct HTTPS connection for
             Metadata & Auth)        |                    |   large video upload & replay)
                                     v                    v
         +-----------------------------+         +-------------------------------+
         |    Vercel API Gateway       | ------> |  Hosted InstantDRS Container  |
         |    (instantdrs-vercel)      |         |  (Railway / Render / VPS)     |
         |  - CORS & Edge Routing      |         |  - Java 21 LTS Spring Boot    |
         |  - Lightweight REST proxy   |         |  - Python 3 + OpenVINO FP32   |
         |  - Max Body: 4.5 MB         |         |  - FFmpeg 6+ / FFprobe        |
         +-----------------------------+         |  - Storage volumes (/app/storage)|
                                                 +-------------------------------+
                                                      |
                                                      v
                                         +------------------+
                                         |  MySQL 8.0 DB    |
                                         |  - Users         |
                                         |  - Sessions      |
                                         |  - Timeline      |
                                         |  - Decisions     |
                                         +------------------+
```

### Direct Connection vs. Vercel Gateway:
* **Direct HTTPS Connection (Recommended for Android):** Android Retrofit client directly targets the backend endpoint (e.g., `https://api.instantdrs.yourdomain.com`). This completely avoids intermediate proxy body size limitations (Vercel 4.5MB cap) and eliminates serverless timeout risks.
* **Vercel API Gateway (Alternative/Hybrid):** Can be used as a public facade for authentication and session queries (`/api/auth/*`, `/api/drs/sessions/*`), but video uploads (>4.5MB) **must** bypass Vercel due to platform payload caps.

---

## 2. Docker Build Context & Structure

To ensure that both the Spring Boot source and the complete Python video engine (with OpenVINO models and weights) are packaged cleanly without security errors, the project directory layout is structured as follows:

```
backend/backend/
├── Dockerfile                  # Multi-stage Java 21 + Python + FFmpeg build
├── .dockerignore               # Whitelists video-engine models; ignores caches
├── docker-compose.yml          # Container orchestration (Backend + MySQL)
├── pom.xml                     # Maven project specification
├── src/                        # Spring Boot source code
│   └── main/resources/
│       ├── application.properties
│       ├── application-prod.properties
│       └── schema.sql          # Hibernate-compatible schema initialization
└── video-engine/               # Complete Python review pipeline & models
    ├── requirements.txt        # CPU-optimized PyTorch, OpenVINO, OpenCV
    ├── pipeline/
    │   ├── fast_drs_pipeline.py
    │   ├── run_pipeline.py
    │   └── yolov8n.pt          # PyTorch model weights (6.5MB)
    └── tool3-sports-ball-tracking/
        ├── ball_tracker.py     # Optical flow + Kalman + YOLO ball tracker
        ├── sports_config.py    # Per-sport model resolution
        ├── yolov8n.pt
        └── yolov8n_openvino_model/
            ├── yolov8n.xml     # OpenVINO FP32 model definition
            ├── yolov8n.bin     # OpenVINO FP32 model weights (13MB)
            └── metadata.yaml
```

### Building the Docker Image

You can build the production Docker image directly from the `backend/backend` folder:

```bash
docker build -t instantdrs-backend:1.0 .
```

---

## 3. Required Environment Variables

When running with the `prod` profile (`--spring.profiles.active=prod` or `SPRING_PROFILES_ACTIVE=prod`), configure the following environment variables:

| Variable | Required | Container Default | Description |
|---|---|---|---|
| `DB_URL` | **Yes** | — | JDBC URL (e.g. `jdbc:mysql://<host>:3306/instant_drs?useSSL=true&serverTimezone=UTC`) |
| `DB_USERNAME` | **Yes** | — | MySQL database username |
| `DB_PASSWORD` | **Yes** | — | MySQL database password |
| `PORT` | No | `8080` | Application HTTP listening port |
| `STORAGE_ORIGINAL_DIR` | No | `/app/storage/original/` | Directory where uploaded original videos are stored |
| `STORAGE_PROCESSED_DIR` | No | `/app/storage/processed/` | Directory where processed review replays are saved |
| `VIDEO_ENGINE_PYTHON` | No | `/opt/venv/bin/python` | Path to Python 3 interpreter in container |
| `VIDEO_ENGINE_PIPELINE` | No | `/app/video-engine/pipeline/run_pipeline.py` | Legacy sequential pipeline path |
| `VIDEO_ENGINE_FAST_PIPELINE` | No | `/app/video-engine/pipeline/fast_drs_pipeline.py` | Fast DRS review pipeline path |
| `VIDEO_ENGINE_USE_FAST` | No | `true` | Set `true` to use the accelerated OpenVINO pipeline |
| `MAX_FILE_SIZE` | No | `100MB` | Multipart maximum upload file size |
| `MAX_REQUEST_SIZE` | No | `100MB` | Multipart maximum request body size |

---

## 4. Database Setup & Initialization

In production, Spring Boot runs with `spring.jpa.hibernate.ddl-auto=validate`. This guarantees that existing records (sessions, decisions, users) are never dropped or mutated unexpectedly.

### Step 1: Apply Schema Initialization
Before launching the application on a clean MySQL database, run `schema.sql`:

```bash
mysql -h <db_host> -u <db_user> -p instant_drs < src/main/resources/schema.sql
```

The script safely provisions:
* `users` — Authentication credentials with BCrypt hashes.
* `drs_sessions` — DRS session states (`CREATED`, `PROCESSING`, `COMPLETED`, `FAILED`).
* `timeline_events` — Microsecond timestamps and event tags (`BALL_RELEASE`, `BALL_PITCH`, `BALL_TOUCH`, etc.).
* `review_decisions` — Human reviewer decisions (`OUT`, `NOT_OUT`, `TOUCH_OUT`) with session audit links.
* `test_entity` — Hibernate schema entity compliance.

---

## 5. Running the Unified Container

### Option A: Standalone Docker Run
```bash
docker run -d \
  --name instantdrs-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:mysql://<db_host>:3306/instant_drs?useSSL=true&serverTimezone=UTC" \
  -e DB_USERNAME="instantdrs_user" \
  -e DB_PASSWORD="<db_password>" \
  -v instantdrs_storage:/app/storage \
  instantdrs-backend:1.0
```

### Option B: Docker Compose (Backend + MySQL 8)
```bash
docker compose up -d
```

---

## 6. Real Video Processing Verification Results

An end-to-end verification of the production pipeline was executed against the backend running with the production profile:

1. **Session Creation:** Created Session ID `19` (`game: volleyball`).
2. **Video Upload:** Uploaded match clip `test.mp4` (788 KB) via `POST /api/drs/sessions/19/video`.
3. **DRS Pipeline Execution:** `POST /api/drs/sessions/19/process`:
   * **Stage 1 (Ball Tracking):** OpenVINO FP32 model processed 250 frames with batch=1 CPU latency optimization in **10.63 seconds**.
   * **Stage 2 (Slow-Motion):** Generated 0.5x slow-motion review presentation in **0.18 seconds**.
   * **Stage 3 (Audio Sync & Mux):** FFmpeg remuxed synchronized audio in **0.62 seconds**.
   * **Total Pipeline Time:** **11.43 seconds**. Output file: `session_19_processed.mp4`.
4. **Replay Video Streaming:** Streamed binary MP4 via `GET /api/drs/sessions/19/video` (1,232,349 bytes verified).
5. **Timeline Events:** Recorded timestamps (`BALL_SPIKE` at 1200ms, `BALL_TOUCH` at 1850ms) via `POST /api/drs/sessions/19/timeline`.
6. **Referee Decision:** Persisted `TOUCH_OUT` decision via `POST /api/drs/sessions/19/decision`. Session status verified as `COMPLETED`.

---

## 7. Hosting Platform Resource Requirements & Railway Setup

### Hardware Resource Requirements
* **CPU:** Minimum 2 vCPUs (4 vCPUs recommended for multi-user review load). OpenVINO leverages AVX-512 / AVX2 instructions for high-throughput inference.
* **Memory:** Minimum 2 GB RAM (4 GB recommended). PyTorch CPU runtime + OpenVINO + Spring Boot typically consume ~1.2–1.8 GB RAM during active video processing.
* **Storage:** 10 GB+ persistent SSD volume attached to `/app/storage`.
* **OS:** Linux x86_64 (Ubuntu 22.04 LTS / Debian 12 / Amazon Linux 2023).

### Railway Cloud Deployment Configuration
When linking this GitHub repository to Railway:
1. **Repository Root Directory:** `/` (the repository root contains `pom.xml`, `Dockerfile`, and `video-engine/`).
2. **Builder:** Dockerfile (`Dockerfile` in the root).
3. **Healthcheck Path:** `/api/health` (Timeout: 100s).
4. **Persistent Volume:** In the Railway service settings under **Volumes**, add a volume mounted to:
   * Mount Path: `/app/storage`
5. **Environment Variables:**
   * `SPRING_PROFILES_ACTIVE`: `prod`
   * `PORT`: `8080` (or leave default `${{PORT}}` injected by Railway)
   * `DB_URL`: `jdbc:mysql://${{MYSQLHOST}}:${{MYSQLPORT}}/${{MYSQLDATABASE}}?useSSL=false&serverTimezone=UTC`
   * `DB_USERNAME`: `${{MYSQLUSER}}`
   * `DB_PASSWORD`: `${{MYSQLPASSWORD}}`
6. **Automatic HTTPS:** Railway automatically provisions an HTTPS domain (e.g. `https://instantdrs-production.up.railway.app`) with TLS, satisfying Android's network security requirements out of the box.

---

## 8. HTTPS Configuration (Let's Encrypt + Nginx)

Android 9+ blocks cleartext HTTP connections. In production, configure an Nginx reverse proxy with TLS:

```nginx
server {
    server_name api.instantdrs.yourdomain.com;

    # Allow up to 100MB video uploads
    client_max_body_size 100M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Disable buffering for real-time video streaming
        proxy_buffering off;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }
}
```

Issue SSL certificate using Certbot:
```bash
sudo certbot --nginx -d api.instantdrs.yourdomain.com
```

---

## 9. Android Release APK Configuration

In your Android project (`build.gradle.kts`):

```kotlin
buildTypes {
    release {
        // Direct backend HTTPS endpoint
        buildConfigField("String", "BASE_URL", "\"https://api.instantdrs.yourdomain.com/\"")
    }
}
```

Configure Retrofit timeouts in `NetworkModule.kt`:
```kotlin
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .build()
```

---

## 10. Known Platform Limitations

1. **Vercel Serverless Function Limits:**
   * Maximum request body size: **4.5 MB**.
   * Video uploads over 4.5 MB routed through Vercel will return `413 Payload Too Large`.
   * Video upload and video streaming must communicate directly with the hosted Spring Boot backend.
2. **Ephemeral File Systems:**
   * Container platforms like Heroku or basic Render web services use ephemeral disks. A persistent volume mounted to `/app/storage` is mandatory to retain uploaded match clips and processed replay videos across container redeployments.
