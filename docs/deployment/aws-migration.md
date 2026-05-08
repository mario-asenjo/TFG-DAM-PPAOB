# Migracion a AWS (guia practica)

Esta guia define un camino progresivo para mover PPAOB desde `docker compose` (local/VPS) a AWS sin rehacer la arquitectura.

## Objetivo

- Mantener la separacion actual por servicios (`backend`, `frontend`, `worker-static`, `worker-dynamic`, `postgres`, `s3`).
- Externalizar secretos y configuracion por entorno.
- Mantener capacidad de rollback sencilla.

## Mapeo recomendado de servicios

| Componente actual | Opcion AWS recomendada |
|---|---|
| `backend` | ECS Fargate service |
| `frontend` | ECS Fargate + ALB, o S3 + CloudFront |
| `worker-static` | ECS Fargate service (sin exposicion publica) |
| `worker-dynamic` | ECS Fargate service (task definition dedicada) |
| PostgreSQL | Amazon RDS for PostgreSQL |
| MinIO/S3 compatible | Amazon S3 |
| Secrets en `.env` | AWS Secrets Manager / SSM Parameter Store |

## Estrategia por fases

### Fase 1: Contenedores listos

- Construir y publicar imagenes en ECR:
  - `backend/Dockerfile`
  - `frontend/Dockerfile`
  - `workers/static/Dockerfile`
  - `workers/dynamic/Dockerfile`
- Congelar tags por release (`vX.Y.Z` + digest).

### Fase 2: Datos gestionados

- Migrar base de datos a RDS PostgreSQL.
- Migrar almacenamiento de objetos a S3.
- Actualizar variables:
  - `DB_URL`, `DB_USER`, `DB_PASSWORD`
  - `S3_ENDPOINT` (vaciar o endpoint AWS), `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`.

### Fase 3: Orquestacion ECS

- Crear cluster ECS (Fargate).
- Definir 4 task definitions (backend/frontend/worker-static/worker-dynamic).
- Exponer solo backend y frontend via ALB.
- Workers sin ingress publico.

### Fase 4: Seguridad operativa minima

- Secrets fuera de Git (Secrets Manager/SSM).
- `APP_RUNTIME_MODE=prod`.
- `REFRESH_COOKIE_SECURE=true`.
- CORS restringido a dominios reales.
- SGs por rol (ALB, app, DB) con minimo privilegio.

### Fase 5: Observabilidad y operacion

- Logs a CloudWatch Logs.
- Alarmas basicas (5xx, reinicios, latencia backend).
- Backups automaticos de RDS y versionado en S3.

## Variables criticas para produccion

Priorizar estas como secretas/obligatorias:

- `JWT_SECRET`
- `DB_PASSWORD`
- `S3_SECRET_KEY`
- `MINIO_ROOT_PASSWORD` (si todavia aplica en etapa transitoria)

Referencia completa: `docs/deployment/env-matrix.md`.

## Despliegue base sugerido

1. Crear repos ECR.
2. Subir imagenes versionadas.
3. Crear RDS + bucket S3.
4. Cargar secretos en Secrets Manager.
5. Crear ECS services y ALB.
6. Desplegar backend y workers.
7. Desplegar frontend apuntando a la URL publica del backend.
8. Ejecutar smoke tests funcionales.

## Nota sobre frontend y `VITE_API_URL`

El frontend actual usa `VITE_API_URL` en build-time. Para cada entorno (staging/prod), construir imagen con el valor correcto:

```bash
docker build -f frontend/Dockerfile -t ppaob-frontend:staging --build-arg VITE_API_URL=https://api-staging.tudominio.com/api/v1 .
```

Si en el futuro quieres evitar rebuild por entorno, se puede migrar a inyeccion runtime mediante plantilla JS de configuracion.

## Criterio de "ready para AWS"

- Todas las imagenes en ECR con version inmutable.
- Secrets fuera de repositorio.
- `APP_RUNTIME_MODE=prod` sin defaults de desarrollo.
- Healthcheck y smoke test verdes tras deploy.
- Plan de rollback documentado (tag anterior + redeploy).
