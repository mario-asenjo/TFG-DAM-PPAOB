# Environment Matrix

This matrix is the deployment contract for `docker compose` environments.

## Shared infrastructure

| Variable | Required in prod-like/prod | Default (dev) |
|---|---|---|
| `APP_RUNTIME_MODE` | Yes | `dev` |
| `POSTGRES_DB` | Yes | `ppaob` |
| `POSTGRES_USER` | Yes | `ppaob` |
| `POSTGRES_PASSWORD` | Yes | `ppaob_dev_password` |
| `DB_HOST` | Yes | `postgres` |
| `DB_PORT` | Yes | `5432` |
| `DB_NAME` | Yes | `ppaob` |
| `DB_USER` | Yes | `ppaob` |
| `DB_PASSWORD` | Yes | `ppaob_dev_password` |
| `MINIO_ROOT_USER` | Yes | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | Yes | `minioadmin_dev_password` |
| `S3_ENDPOINT` | Yes | `http://minio:9000` |
| `S3_REGION` | Yes | `us-east-1` |
| `S3_ACCESS_KEY` | Yes | `minioadmin` |
| `S3_SECRET_KEY` | Yes | `minioadmin_dev_password` |
| `S3_BUCKET` | Yes | `ppaob-binaries` |
| `S3_PATH_STYLE` | Yes | `true` |

## Backend

| Variable | Required in prod-like/prod | Default (dev) |
|---|---|---|
| `PORT` | Yes | `8080` |
| `DB_URL` | Yes | `jdbc:postgresql://postgres:5432/ppaob` |
| `JWT_SECRET` | Yes | `change-this-in-production-change-this-in-production` |
| `JWT_TTL_MINUTES` | No | `15` |
| `REFRESH_TTL_DAYS` | No | `14` |
| `REFRESH_COOKIE_NAME` | No | `ppaob_refresh` |
| `REFRESH_COOKIE_SECURE` | Yes | `false` |
| `REFRESH_COOKIE_SAME_SITE` | No | `Lax` |
| `REFRESH_COOKIE_PATH` | No | `/api/v1/auth` |
| `MAX_UPLOAD_FILE_SIZE` | No | `20MB` |
| `MAX_UPLOAD_REQUEST_SIZE` | No | `20MB` |
| `APP_UPLOAD_MAX_BYTES` | No | `20971520` |
| `APP_AUDIT_SYSTEM_USER_EMAIL` | No | `system@ppaob.local` |
| `APP_CORS_ALLOWED_ORIGINS` | Yes | `http://localhost:5173,http://127.0.0.1:5173` |

## Frontend

| Variable | Required in prod-like/prod | Default (dev) |
|---|---|---|
| `VITE_API_URL` | Yes | `http://localhost:8080/api/v1` |

## Workers

| Variable | Required in prod-like/prod | Default (dev) |
|---|---|---|
| `WORKER_POLL_SECONDS` | No | `3` |
| `WORKER_LOG_LEVEL` | No | `INFO` |
| `WORKER_PROFILE` | No | `DYNAMIC_BASELINE` |
| `APP_CORRELATION_ENV_PROFILE` | No | `LINUX_SERVER` |
| `APP_CORRELATION_OBSERVED_RUNS` | No | `1` |
| `APP_DEPLOYMENT_EXPOSURE` | No | `INTERNAL` |
| `APP_DEPLOYMENT_PRIVILEGE_LEVEL` | No | `USER` |
| `APP_DEPLOYMENT_DATA_SENSITIVITY` | No | `MEDIUM` |
| `DYNAMIC_AGENT_TIMEOUT_MS` | No | `5000` |
| `DYNAMIC_AGENT_PATH` | No | `/opt/agent-dynamic-c/agent_dynamic` |

## Production minimum policy

- Never keep development defaults for `JWT_SECRET`, `DB_PASSWORD`, `S3_SECRET_KEY`.
- Use `APP_RUNTIME_MODE=prod-like` (or `prod`) and `REFRESH_COOKIE_SECURE=true`.
- Do not allow localhost values in `APP_CORS_ALLOWED_ORIGINS`.
