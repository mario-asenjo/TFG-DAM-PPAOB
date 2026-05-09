CREATE TABLE refresh_sessions (
  session_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  token_hash         CHAR(64) NOT NULL UNIQUE,
  expires_at         TIMESTAMPTZ NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_ip         VARCHAR(64),
  created_user_agent TEXT,
  revoked_at         TIMESTAMPTZ,
  replaced_by        UUID REFERENCES refresh_sessions(session_id),
  revoked_reason     VARCHAR(32)
);

CREATE INDEX idx_refresh_sessions_user_id ON refresh_sessions(user_id);
CREATE INDEX idx_refresh_sessions_expires_at ON refresh_sessions(expires_at);
CREATE INDEX idx_refresh_sessions_revoked_at ON refresh_sessions(revoked_at);
