INSERT INTO users(email, password_hash, enabled)
VALUES ('admin@ppaob.local', '$2b$10$nGWY.kQgbWlIg/NDUmwcU.txur3Sgq/qdqYTcj49nPIwSIA4tFGje', TRUE)
ON CONFLICT (email)
DO UPDATE SET password_hash = EXCLUDED.password_hash,
              enabled = TRUE;

INSERT INTO user_roles(user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@ppaob.local'
ON CONFLICT DO NOTHING;
