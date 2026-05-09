INSERT INTO users(email, password_hash, enabled)
VALUES ('admin@ppaob.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiS6x6byN1Nsx3Rp3XIanFkFJxux1Fa', TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles(user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@ppaob.local'
ON CONFLICT DO NOTHING;
