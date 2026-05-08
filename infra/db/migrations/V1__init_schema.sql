-- =========================
-- PPAOB - Schema v1
-- PostgreSQL 16+
-- =========================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- AUTH: users / roles
-- =========================

CREATE TABLE roles (
  role_id   SMALLSERIAL PRIMARY KEY,
  name      VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE users (
  user_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email          VARCHAR(255) NOT NULL UNIQUE,
  password_hash  VARCHAR(255) NOT NULL,
  enabled        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
  user_id    UUID NOT NULL REFERENCES users(user_id),
  role_id    SMALLINT NOT NULL REFERENCES roles(role_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);

-- Seed base roles (safe to re-run)
INSERT INTO roles(name) VALUES ('ADMIN')   ON CONFLICT DO NOTHING;
INSERT INTO roles(name) VALUES ('ANALYST') ON CONFLICT DO NOTHING;
INSERT INTO roles(name) VALUES ('VIEWER')  ON CONFLICT DO NOTHING;

-- =========================
-- STORAGE: S3/MinIO object references
-- =========================

CREATE TABLE stored_objects (
  object_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider         VARCHAR(32) NOT NULL DEFAULT 'S3',   -- S3, LOCAL, ...
  bucket           VARCHAR(255) NOT NULL,
  object_key       TEXT NOT NULL,
  checksum_sha256  CHAR(64),
  size_bytes       BIGINT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (bucket, object_key)
);

-- =========================
-- BINARIES: uploaded executables (dedupe by sha256)
-- =========================

CREATE TABLE binaries (
  binary_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  original_name   TEXT NOT NULL,
  sha256          CHAR(64) NOT NULL UNIQUE,
  format          VARCHAR(16) NOT NULL,   -- ELF / PE / UNKNOWN
  size_bytes      BIGINT NOT NULL,
  uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  uploaded_by     UUID NOT NULL REFERENCES users(user_id),
  object_id       UUID NOT NULL REFERENCES stored_objects(object_id)
);

CREATE INDEX idx_binaries_uploaded_at ON binaries(uploaded_at);
CREATE INDEX idx_binaries_uploaded_by ON binaries(uploaded_by);

-- =========================
-- ANALYSES: analysis jobs + lifecycle
-- =========================

CREATE TABLE analyses (
  analysis_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  binary_id       UUID NOT NULL REFERENCES binaries(binary_id),
  requested_by    UUID NOT NULL REFERENCES users(user_id),

  profile         VARCHAR(16) NOT NULL,   -- STATIC / DYNAMIC / FULL
  status          VARCHAR(16) NOT NULL,   -- CREATED / QUEUED / RUNNING / DONE / FAILED

  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at      TIMESTAMPTZ,
  finished_at     TIMESTAMPTZ,

  error_summary   TEXT
);

CREATE INDEX idx_analyses_created_at  ON analyses(created_at);
CREATE INDEX idx_analyses_status      ON analyses(status);
CREATE INDEX idx_analyses_requested_by ON analyses(requested_by);
CREATE INDEX idx_analyses_binary_id   ON analyses(binary_id);

-- =========================
-- RESULTS: JSONB payload (static/dynamic/score/evidence)
-- 1:1 with analyses
-- =========================

CREATE TABLE analysis_results (
  result_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  analysis_id     UUID NOT NULL UNIQUE REFERENCES analyses(analysis_id) ON DELETE CASCADE,
  schema_version  INTEGER NOT NULL DEFAULT 1,
  results_json    JSONB NOT NULL,
  stored_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================
-- ARTIFACTS: generated outputs (HTML report, exported JSON, etc.)
-- =========================

CREATE TABLE artifacts (
  artifact_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  analysis_id     UUID NOT NULL REFERENCES analyses(analysis_id) ON DELETE CASCADE,
  type            VARCHAR(16) NOT NULL,   -- HTML / JSON / OTHER
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  object_id       UUID NOT NULL REFERENCES stored_objects(object_id)
);

CREATE INDEX idx_artifacts_analysis_id ON artifacts(analysis_id);

-- =========================
-- AUDIT: append-only events
-- =========================

CREATE TABLE audit_events (
  event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ts              TIMESTAMPTZ NOT NULL DEFAULT now(),

  action          VARCHAR(64) NOT NULL,   -- LOGIN / UPLOAD / ANALYSIS_START / REPORT_DOWNLOAD / ...
  result          VARCHAR(16) NOT NULL,   -- SUCCESS / FAIL

  user_id         UUID NOT NULL REFERENCES users(user_id),
  analysis_id     UUID REFERENCES analyses(analysis_id),
  binary_id       UUID REFERENCES binaries(binary_id),

  details         JSONB
);

CREATE INDEX idx_audit_ts       ON audit_events(ts);
CREATE INDEX idx_audit_user     ON audit_events(user_id);
CREATE INDEX idx_audit_action   ON audit_events(action);
CREATE INDEX idx_audit_analysis ON audit_events(analysis_id);
CREATE INDEX idx_audit_binary   ON audit_events(binary_id);
