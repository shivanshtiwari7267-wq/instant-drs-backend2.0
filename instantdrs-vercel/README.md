# InstantDRS — Vercel Backend (v2.0)

Native serverless REST API for the InstantDRS Android application.  
Replaces the Spring Boot + Railway deployment with Vercel Functions + PlanetScale + Cloudinary.

---

## Architecture

```
Android App
  ├─ Auth / session / timeline / decision  →  Vercel Functions (this project)
  ├─ Video processing                       →  On-device (FFmpegKit + TFLite YOLOv8n)
  └─ Video storage                          →  Cloudinary (direct upload, CDN URL)
                                                   ↓
                                            stored in PlanetScale as processed_video_url
```

---

## API Endpoints

All base paths are relative to your Vercel deployment URL (e.g. `https://instantdrs.vercel.app`).

### Auth (Public Endpoints)
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Register a new user (returns userId + username + JWT token) |
| `POST` | `/api/auth/login` | Login (returns userId + username + JWT token) |

### DRS Sessions (Private — requires `Authorization: Bearer <token>`)
All DRS endpoints enforce multi-tenant isolation: users can only view and manage their own sessions. Any attempt to access another user's session returns `403 Forbidden`.

| Method | Path | Description |
|--------|------|-------------|
| `POST`  | `/api/drs/sessions` | Create session (associated with authenticated user) |
| `GET`   | `/api/drs/sessions` | List sessions owned by authenticated user |
| `GET`   | `/api/drs/sessions/:id` | Get session by ID (ownership verified) |
| `PATCH` | `/api/drs/sessions/:id` | Update status / processedVideoUrl / decision (ownership verified) |
| `POST`  | `/api/drs/sessions/:id/upload-token` | Get signed Cloudinary upload token (ownership verified) |

### Timeline Events
| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/drs/sessions/:id/timeline` | List events (sorted by timestampMs ASC) |
| `POST` | `/api/drs/sessions/:id/timeline` | Create event |
| `GET`  | `/api/drs/sessions/:id/timeline/:eventId` | Get single event |

### Review Decisions
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/drs/sessions/:id/decision` | Save review decision |
| `POST` | `/api/drs/sessions/:id/review-decision` | Alias |
| `GET`  | `/api/drs/sessions/:id/decision` | Get latest decision |
| `GET`  | `/api/drs/sessions/:id/review-decision` | Alias |
| `GET`  | `/api/drs/sessions/:id/decisions` | Audit trail (all decisions) |

### Health
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/gateway-health` | Gateway health check |
| `GET` | `/api/health` | Alias |

---

## Setup

### 1. PlanetScale Database

1. Create a [PlanetScale](https://planetscale.com) account and new database: `instant-drs`
2. Create a development branch: `pscale branch create instant-drs dev`
3. Run the schema: paste contents of `schema.sql` in the PlanetScale console
4. Create a connection password: PlanetScale Dashboard → Connect → @planetscale/database
5. Copy the connection URL into `DATABASE_URL`

### 2. Cloudinary

1. Create a [Cloudinary](https://cloudinary.com) account
2. Get your Cloud Name, API Key, and API Secret from the dashboard
3. Set `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`

### 3. Environment Variables

Copy `.env.example` to `.env.local` for local dev:

```bash
cp .env.example .env.local
# Fill in your real values
```

Set the same variables in **Vercel Dashboard → Project → Settings → Environment Variables**.

### 4. Local Development

```bash
npm install
npm run dev        # starts vercel dev on http://localhost:3000
```

### 5. Deploy

```bash
npm run deploy     # deploys to Vercel production
```

---

## Video Upload Flow (Android)

1. Android records / imports video clip
2. On-device processing: FFmpegKit slow-motion + TFLite ball tracking
3. `POST /api/drs/sessions/:id/upload-token` → receives signed Cloudinary params
4. Android uploads processed MP4 directly to Cloudinary
5. Cloudinary returns CDN URL
6. `PATCH /api/drs/sessions/:id` with `{ status: "COMPLETED", processedVideoUrl: "https://res.cloudinary.com/..." }`
7. Android plays video from `processedVideoUrl` using ExoPlayer

---

## Spring Boot Backward Compatibility Notes

- All original Android JSON field shapes are preserved
- `AuthResponse` shape (`success`, `message`, `userId`, `username`) — unchanged
- `DrsSession` shape — `originalVideoPath` and `processedVideoPath` are always `null` (fields exist in response for null-safe Android compatibility); `processedVideoUrl` is the new CDN field
- `TimelineEventResponse` dual fields (`eventType`/`type`, `timestampMs`/`timestamp`, `durationMs`/`duration`) — preserved
- `ReviewDecisionResponse` dual field (`decision`/`reviewDecision`) — preserved
- All field aliases in request bodies preserved (`reviewDecision`, `userDecision`, `eventId`, `timestamp`, etc.)
- BCrypt algorithm is identical — existing user passwords stored in MySQL work without migration

---

## What Was Removed

| Removed | Reason |
|---------|--------|
| `POST /api/drs/sessions/:id/video` | Vercel 4.5 MB body limit; video goes direct to Cloudinary |
| `POST /api/drs/sessions/:id/process` | Python/OpenVINO pipeline runs on Android device instead |
| `GET /api/drs/sessions/:id/video` | No server-side video storage; use `processedVideoUrl` CDN URL |
| Old `[...path].js` gateway | Replaced by native Vercel Function routes |
