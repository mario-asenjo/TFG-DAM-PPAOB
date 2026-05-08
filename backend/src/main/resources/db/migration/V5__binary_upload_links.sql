CREATE TABLE binary_uploads (
  upload_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  binary_id      UUID NOT NULL REFERENCES binaries(binary_id) ON DELETE CASCADE,
  user_id        UUID NOT NULL REFERENCES users(user_id),
  uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  source         VARCHAR(16) NOT NULL,
  UNIQUE (binary_id, user_id)
);

CREATE INDEX idx_binary_uploads_user_id ON binary_uploads(user_id);
CREATE INDEX idx_binary_uploads_binary_id ON binary_uploads(binary_id);

INSERT INTO binary_uploads(binary_id, user_id, uploaded_at, source)
SELECT b.binary_id, b.uploaded_by, b.uploaded_at, 'NEW_UPLOAD'
FROM binaries b
ON CONFLICT (binary_id, user_id) DO NOTHING;
