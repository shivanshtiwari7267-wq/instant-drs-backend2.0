-- ============================================================
-- InstantDRS — PlanetScale Database Schema
-- ============================================================
-- Run this SQL in your PlanetScale branch console or via
--   pscale shell <database> <branch>
--
-- Preserves the original Spring Boot JPA entity structure.
-- Changes from the MySQL schema:
--   1. drs_sessions: replaced original_video_path / processed_video_path
--      (local filesystem paths) with processed_video_url (CDN URL).
--      original_video_path is removed — Android no longer uploads to server.
--   2. All DATETIME columns use DATETIME(6) for microsecond precision.
--   3. Indexes added for common query patterns.
--   4. PlanetScale does not support FK constraint enforcement by default
--      (it uses Vitess which disables FK checks for horizontal scaling),
--      so FK declarations are omitted but column references are preserved.
-- ============================================================

-- ============================================================
-- 1. Users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  username   VARCHAR(50)  NOT NULL,
  password   VARCHAR(255) NOT NULL COMMENT 'BCrypt hash (10 rounds)',
  role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
  created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. DRS Sessions
-- ============================================================
CREATE TABLE IF NOT EXISTS drs_sessions (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  user_id               BIGINT        NOT NULL COMMENT 'References users.id (owner of this DRS session)',
  game                  VARCHAR(255)  NOT NULL COMMENT 'Sport name: cricket, volleyball, etc.',
  status                VARCHAR(50)   NOT NULL DEFAULT 'CREATED'
                          COMMENT 'CREATED | PROCESSING | COMPLETED | FAILED',

  -- Video storage: CDN URL set by Android after Cloudinary upload
  -- (replaces the legacy original_video_path / processed_video_path filesystem paths)
  processed_video_url   VARCHAR(2048)     NULL COMMENT 'Cloudinary CDN URL of processed review video',

  -- Legacy fields kept as NULL columns for backward Android client compatibility
  -- Android clients that read these fields will receive null (safe for null handling)
  -- original_video_path is omitted — Android never uploads to server anymore
  -- processed_video_path is omitted — replaced by processed_video_url

  decision              VARCHAR(255)      NULL COMMENT 'Latest review decision (OUT, NOT_OUT, IN, etc.)',
  created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at          DATETIME(6)       NULL,

  PRIMARY KEY (id),
  KEY idx_sessions_user    (user_id),
  KEY idx_sessions_game    (game),
  KEY idx_sessions_status  (status),
  KEY idx_sessions_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. Timeline Events
-- ============================================================
CREATE TABLE IF NOT EXISTS timeline_events (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  session_id    BIGINT        NOT NULL COMMENT 'References drs_sessions.id',
  event_type    VARCHAR(255)  NOT NULL COMMENT 'E.g. BALL_RELEASE, BALL_PITCH, BALL_IMPACT, WICKET_HIT',
  timestamp_ms  BIGINT        NOT NULL COMMENT 'Video timestamp in milliseconds',
  duration_ms   BIGINT            NULL COMMENT 'Optional event duration in milliseconds',
  description   VARCHAR(1000)     NULL,
  created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

  PRIMARY KEY (id),
  KEY idx_tl_session_ts (session_id, timestamp_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. Review Decisions
-- ============================================================
CREATE TABLE IF NOT EXISTS review_decisions (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  session_id        BIGINT        NOT NULL COMMENT 'References drs_sessions.id',
  decision          VARCHAR(255)  NOT NULL COMMENT 'OUT | NOT_OUT | IN | OVERTURNED | etc.',
  selected_event_id BIGINT            NULL COMMENT 'Optional: timeline_events.id linked to this decision',
  notes             VARCHAR(2000)     NULL,
  reviewed_by       VARCHAR(255)      NULL COMMENT 'Umpire / reviewer identifier',
  reviewed_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

  PRIMARY KEY (id),
  KEY idx_rd_session_reviewed (session_id, reviewed_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
