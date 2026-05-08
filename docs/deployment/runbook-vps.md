# VPS Runbook (Docker Compose)

## 1) First deploy

1. Copy `.env.example` to `.env` and set real secrets.
2. Start services in prod-like mode:

```bash
docker compose -f infra/compose/docker-compose.yml -f infra/compose/docker-compose.prod.yml up -d --build
```

3. Check health:

```bash
curl -f http://localhost:8080/api/v1/health
```

If configuration is unsafe or incomplete in `prod-like/prod`, backend/worker startup will fail fast.

## 2) Update deploy

```bash
docker compose -f infra/compose/docker-compose.yml -f infra/compose/docker-compose.prod.yml pull
docker compose -f infra/compose/docker-compose.yml -f infra/compose/docker-compose.prod.yml up -d --build
```

## 3) Rollback

1. Switch to last known stable image tags or commit.
2. Re-run `up -d --build` with same compose files.
3. Validate health endpoint and smoke flow.

## 4) Backup minimum

- PostgreSQL volume: `ppaob_pgdata`.
- MinIO volume: `ppaob_minio`.
- Backup cadence: at least daily snapshot for both volumes.

## 5) Pre-release checklist

- `.env` has no development secrets.
- `APP_RUNTIME_MODE=prod-like` or `prod`.
- `REFRESH_COOKIE_SECURE=true`.
- CORS list has only real frontend origins.
- CI/test suite passes (`make test`).
- Smoke test passes (`make smoke`).
