# PPAOB

<p align="center">
  <strong>Plataforma de pre-explotacion para analisis de binarios, con foco en trazabilidad, seguridad operativa y priorizacion realista de riesgo.</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/Licencia-MIT-green?style=for-the-badge" alt="Licencia"></a>
  <a href=".github/workflows/ci.yml"><img src="https://img.shields.io/badge/CI-GitHub_Actions-blue?style=for-the-badge" alt="CI"></a>
  <a href="docs/deployment/env-matrix.md"><img src="https://img.shields.io/badge/Deploy-Dev%20%7C%20Prod--like-orange?style=for-the-badge" alt="Deploy"></a>
</p>

PPAOB (Plataforma de Pre-Explotación y Análisis Ofensivo de Binarios) permite estudiar binarios ELF en un entorno controlado antes de cualquier fase ofensiva. Combina analisis estatico, analisis dinamico acotado, correlacion contextual y una interfaz de triage para equipos tecnicos.

---

## Que ofrece hoy

| Area | Capacidades |
|---|---|
| Backend | API REST con auth JWT + refresh rotativo, RBAC, auditoria y reportes |
| Analisis | Profiles `STATIC_BASELINE` y `DYNAMIC_BASELINE` con persistencia JSONB |
| Riesgo | Correlacion por perfil de entorno (`LINUX_SERVER` / `CONTAINER_SERVICE`) |
| Frontend | Consola React/Vite para auth, binarios, analisis, reportes y auditoria |
| Infra | Stack Docker Compose con PostgreSQL, MinIO, backend y workers |

---

## Inicio rapido

### Prerrequisitos

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker + Docker Compose

### 1) Preparar variables de entorno

```bash
cp .env.example .env
```

Para desarrollo local puedes dejar los defaults. Para `prod-like/prod`, usa secretos reales.

### 2) Levantar stack en local (perfil dev)

```bash
make up
```

Endpoints principales:

- API: `http://localhost:8080`
- Frontend (contenedor Docker): `http://localhost:5173`

### 3) Perfil endurecido (prod-like)

```bash
make up-prod
```

Este perfil exige variables criticas y aplica validaciones de seguridad al arranque.

Apagado de stack:

```bash
make down
make down-prod
```

### 4) Verificacion rapida

```bash
make smoke
make test
```

`make smoke` ejecuta `tools/smoke_e2e.sh`. Puedes personalizar `API_BASE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `SAMPLE_BIN` por variables de entorno (ver cabecera del script).

---

## Baseline tecnico (resumen)

### API disponible

- Auth: `register`, `login`, `refresh`, `logout`, `me`
- Salud: `GET /api/v1/health`
- Binarios: upload ELF, listado y deduplicacion por SHA-256
- Analisis: creacion, estado, listado y resultados
- Reportes: generacion HTML y descarga
- Admin/Auditoria: eventos, usuarios y gestion de roles

### Flujo de analisis

1. El backend registra el job en `analyses` como `PENDING`.
2. El worker correspondiente realiza claim atomico (`FOR UPDATE SKIP LOCKED`).
3. Descarga binario desde S3/MinIO.
4. Ejecuta analisis estatico o dinamico.
5. Aplica correlacion contextual y calcula `riskScore` + `priority`.
6. Persiste resultado en `analysis_results`.
7. La UI consume `GET /api/v1/analyses/{id}/results` para triage.

### Auth y seguridad

- Access token JWT de corta vida (por defecto, 15 min).
- Refresh token en cookie HttpOnly con rotacion en cada refresh.
- RBAC: `VIEWER`, `ANALYST`, `ADMIN`.
- Contrato de error unificado: `timestamp`, `status`, `error`, `message`, `path`, `details`.

### Correlacion y priorizacion

- Perfil seleccionable por entorno: `APP_CORRELATION_ENV_PROFILE`.
- Contexto de despliegue configurable: exposicion, privilegio, sensibilidad y evidencia de ejecuciones.
- Salida enriquecida en `results_json.correlation` con razones de priorizacion legibles.

---

## Arquitectura

```text
frontend (React/Vite)
        |
        v
backend API (Spring Boot)
        |
        +--> PostgreSQL (estado, auth, resultados, auditoria)
        |
        +--> S3/MinIO (binarios y artefactos)
        |
        +--> workers/static (Python)
        |
        +--> workers/dynamic (Python + agent-dynamic-c)
```

Servicios y componentes:

- `frontend/`: UI de operador y triage
- `backend/`: orquestacion, reglas de negocio y seguridad
- `workers/static/`: analisis estatico baseline
- `workers/dynamic/`: analisis dinamico baseline
- `agent-dynamic-c/`: traza dinamica minima con `ptrace` + `seccomp`
- `infra/compose/`: despliegue local y `prod-like`

---

## Dockerfiles y empaquetado

El repositorio incluye Dockerfiles para:

- `backend/Dockerfile`
- `workers/static/Dockerfile`
- `workers/dynamic/Dockerfile`
- `frontend/Dockerfile`

Build rapido del frontend containerizado:

```bash
docker build -f frontend/Dockerfile -t ppaob-frontend:local --build-arg VITE_API_URL=http://localhost:8080/api/v1 .
```

---

## Preparacion para VPS/AWS

Documentacion recomendada de despliegue:

- Matriz de variables: `docs/deployment/env-matrix.md`
- Runbook VPS: `docs/deployment/runbook-vps.md`
- Guia de migracion AWS: `docs/deployment/aws-migration.md`

Nota: el proyecto esta optimizado para laboratorio controlado y entornos autorizados.

---

## Rutas principales de frontend

- `#/login`
- `#/register`
- `#/dashboard`
- `#/binaries`
- `#/analyses`
- `#/reports`
- `#/audit` (ADMIN)
- `#/admin-users` (ADMIN)

---

## Solucion de problemas rapida

### Flyway checksum mismatch

Si cambian migraciones y la base persistente conserva checksums anteriores:

```bash
docker compose -f infra/compose/docker-compose.yml down -v
docker compose -f infra/compose/docker-compose.yml up -d --build
```

---

## Licencia

MIT. Ver `LICENSE`.
