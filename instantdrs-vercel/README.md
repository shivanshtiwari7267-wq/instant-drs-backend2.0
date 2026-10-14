# InstantDRS Vercel API Gateway

This directory contains a lightweight Vercel-compatible gateway configuration for the InstantDRS backend.

## Architecture Overview

```
                      +-----------------------------+
                      |       Android Release APK    |
                      +-----------------------------+
                        /                         \
                       / (Auth, Sessions,          \ (Direct Video Uploads
                      /   Timeline, Decisions)      \  & Video Replay Streaming)
                     v                               v
         +------------------------+      +---------------------------+
         | Vercel API Gateway     |      | Hosted Spring Boot Backend|
         | (instantdrs-vercel)    | ---> | (Railway / Render / VPS)  |
         | - Path forwarding      |      | - Java 21 Spring Boot     |
         | - CORS handling        |      | - Video Processing Engine |
         | - Health checks        |      | - MySQL 8 Database        |
         +------------------------+      +---------------------------+
```

## Supported Endpoints
The gateway forwards the following REST endpoints to `${BACKEND_URL}`:
* `GET /api/health`
* `POST /api/auth/register`
* `POST /api/auth/login`
* `POST /api/drs/sessions`
* `GET /api/drs/sessions`
* `GET /api/drs/sessions/{id}`
* `POST /api/drs/sessions/{id}/process`
* `GET /api/drs/sessions/{id}/timeline`
* `POST /api/drs/sessions/{id}/timeline`
* `GET /api/drs/sessions/{id}/timeline/{eventId}`
* `POST /api/drs/sessions/{id}/decision`
* `GET /api/drs/sessions/{id}/decision`
* `GET /api/drs/sessions/{id}/decisions`

## Platform Limits & Video Operations Notice

> [!WARNING]
> **Vercel Serverless Function Limits:**
> 1. **Request Body Limit:** Vercel enforces a strict **4.5 MB** maximum payload limit on all serverless requests.
> 2. **Execution Timeout:** Serverless functions timeout after **10 seconds** (Hobby) or **60 seconds** (Pro).
>
> **Recommended Android Configuration:**
> * For video uploads (`POST /api/drs/sessions/{id}/video`, up to 100MB) and binary replay streaming (`GET /api/drs/sessions/{id}/video`), the Android application should communicate **directly with the hosted backend HTTPS endpoint** (e.g. `https://your-backend.railway.app`).
> * If preferred, the Android application can communicate directly with the hosted backend for all operations, making the Vercel gateway optional.

## Setup Instructions

1. Install the Vercel CLI (optional):
   ```bash
   npm install -g vercel
   ```

2. Link and deploy to Vercel:
   ```bash
   cd instantdrs-vercel
   vercel
   ```

3. Configure Environment Variables in the Vercel Dashboard:
   * Key: `BACKEND_URL`
   * Value: `https://<your-real-deployed-backend-domain>` (e.g., `https://instantdrs-api.railway.app`)

4. Redeploy or deploy to production:
   ```bash
   vercel --prod
   ```
