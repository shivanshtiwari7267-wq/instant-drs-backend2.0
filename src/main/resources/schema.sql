-- ==========================================================
-- InstantDRS Production Database Schema Initialization Script
-- Compatible with MySQL 8.0+ and Hibernate ddl-auto=validate
-- Preserves existing data (safe IF NOT EXISTS clauses)
-- ==========================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'USER',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. DRS Sessions Table
CREATE TABLE IF NOT EXISTS drs_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game VARCHAR(255) NULL,
    status VARCHAR(255) NULL,
    original_video_path VARCHAR(255) NULL,
    processed_video_path VARCHAR(255) NULL,
    decision VARCHAR(255) NULL,
    created_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Timeline Events Table
CREATE TABLE IF NOT EXISTS timeline_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    timestamp_ms BIGINT NOT NULL,
    duration_ms BIGINT NULL,
    description VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_timeline_session (session_id),
    CONSTRAINT fk_timeline_session
        FOREIGN KEY (session_id) REFERENCES drs_sessions(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Review Decisions Table
CREATE TABLE IF NOT EXISTS review_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    decision VARCHAR(255) NOT NULL,
    selected_event_id BIGINT NULL,
    notes VARCHAR(2000) NULL,
    reviewed_by VARCHAR(255) NULL,
    reviewed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_decision_session (session_id),
    CONSTRAINT fk_decision_session
        FOREIGN KEY (session_id) REFERENCES drs_sessions(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Test Entity Table (Maintains Hibernate Entity Mapping Validation)
CREATE TABLE IF NOT EXISTS test_entity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
