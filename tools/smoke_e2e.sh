#!/usr/bin/env bash
set -euo pipefail

# Smoke E2E - Validacion funcional de la plataforma completa.
#
# Que valida (9 pasos):
# 1) Health backend
# 2) Registro/login de usuario smoke
# 3) Login de admin
# 4) Upload de binario ELF
# 5) Ejecucion STATIC_BASELINE
# 6) Ejecucion DYNAMIC_BASELINE
# 7) Consulta de resultados dinamicos
# 8) Generacion y descarga de reporte
# 9) Consulta de auditoria con filtros
#
# Prerrequisitos:
# - Stack levantado (make up / make up-prod)
# - curl y jq instalados
# - Binario de ejemplo disponible (ver SAMPLE_BIN)
#
# Variables configurables:
# - API_BASE (default: http://localhost:8080/api/v1)
# - ADMIN_EMAIL (default: admin@ppaob.local)
# - ADMIN_PASSWORD (default: password)
# - SAMPLE_BIN (default: example_programs/unsafe_strcpy)
#
# Ejemplos:
# - make smoke
# - API_BASE=http://localhost:8080/api/v1 bash tools/smoke_e2e.sh
# - SAMPLE_BIN=example_programs/command_exec_system bash tools/smoke_e2e.sh
#
# Salida esperada: "Smoke E2E completed successfully"

API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ppaob.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-password}"
SAMPLE_BIN="${SAMPLE_BIN:-example_programs/unsafe_strcpy}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

json_get() {
  local json="$1"
  local query="$2"
  echo "$json" | jq -r "$query"
}

wait_done() {
  local token="$1"
  local analysis_id="$2"
  # Espera activa de analisis con timeout total ~120s (60 intentos * 2s).
  local max_attempts=60
  local attempt=1

  while [ "$attempt" -le "$max_attempts" ]; do
    local status_json
    status_json=$(curl -sS -H "Authorization: Bearer $token" "$API_BASE/analyses/$analysis_id")
    local status
    status=$(json_get "$status_json" '.status')

    if [ "$status" = "DONE" ]; then
      echo "Analysis $analysis_id DONE"
      return 0
    fi
    if [ "$status" = "FAILED" ]; then
      echo "Analysis $analysis_id FAILED" >&2
      echo "$status_json" >&2
      return 1
    fi
    sleep 2
    attempt=$((attempt + 1))
  done

  echo "Timed out waiting analysis $analysis_id" >&2
  return 1
}

require_cmd curl
require_cmd jq

if [ ! -f "$SAMPLE_BIN" ]; then
  echo "Sample ELF not found: $SAMPLE_BIN" >&2
  exit 1
fi

echo "[1/9] Health check"
# Se esperan hasta 30 intentos para evitar fallos por arranque en frio.
for attempt in $(seq 1 30); do
  if curl -sS "$API_BASE/health" | jq '.' >/dev/null 2>&1; then
    break
  fi
  sleep 2
  if [ "$attempt" -eq 30 ]; then
    echo "Backend health did not become ready" >&2
    exit 1
  fi
done

echo "[2/9] Register + login smoke user"
SMOKE_EMAIL="smoke.$(date +%s)@ppaob.local"
SMOKE_PASSWORD="SmokePass123!"
curl -sS -X POST "$API_BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}" >/dev/null

SMOKE_LOGIN=$(curl -sS -X POST "$API_BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}")
SMOKE_TOKEN=$(json_get "$SMOKE_LOGIN" '.token')
test "$SMOKE_TOKEN" != "null"

echo "[3/9] Login admin"
ADMIN_LOGIN=$(curl -sS -X POST "$API_BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
ADMIN_TOKEN=$(json_get "$ADMIN_LOGIN" '.token')
test "$ADMIN_TOKEN" != "null"

echo "[4/9] Upload ELF"
# Requiere token ADMIN porque el binario queda vinculado al propietario.
UPLOAD_JSON=$(curl -sS -X POST "$API_BASE/binaries" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "file=@$SAMPLE_BIN")
BINARY_ID=$(json_get "$UPLOAD_JSON" '.binaryId')
test "$BINARY_ID" != "null"

echo "[5/9] Run STATIC_BASELINE"
# Validamos path estatico completo: scheduler -> worker-static -> persistencia.
STATIC_RUN=$(curl -sS -X POST "$API_BASE/analyses" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"binaryId\":\"$BINARY_ID\",\"profile\":\"STATIC_BASELINE\"}")
STATIC_ID=$(json_get "$STATIC_RUN" '.analysisId')
wait_done "$ADMIN_TOKEN" "$STATIC_ID"

echo "[6/9] Run DYNAMIC_BASELINE"
# Validamos path dinamico completo: scheduler -> worker-dynamic -> agente C.
DYNAMIC_RUN=$(curl -sS -X POST "$API_BASE/analyses" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"binaryId\":\"$BINARY_ID\",\"profile\":\"DYNAMIC_BASELINE\"}")
DYNAMIC_ID=$(json_get "$DYNAMIC_RUN" '.analysisId')
wait_done "$ADMIN_TOKEN" "$DYNAMIC_ID"

echo "[7/9] Fetch dynamic results"
# Comprobacion minima de estructura dinamica para detectar regresiones de contrato.
DYNAMIC_RESULTS=$(curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE/analyses/$DYNAMIC_ID/results")
json_get "$DYNAMIC_RESULTS" '.results.dynamic.runtime.exitCode' >/dev/null

echo "[8/9] Generate + download report"
# Generacion de artefacto HTML y descarga autenticada.
REPORT_JSON=$(curl -sS -X POST "$API_BASE/reports" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"analysisId\":\"$DYNAMIC_ID\",\"type\":\"HTML\"}")
ARTIFACT_ID=$(json_get "$REPORT_JSON" '.artifactId')
test "$ARTIFACT_ID" != "null"
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE/reports/$ARTIFACT_ID/download" >/dev/null

echo "[9/9] Query audit with filters"
# Verifica endpoint ADMIN de auditoria con filtros combinados.
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE/audit/events?action=ANALYSIS_STATUS_UPDATE&result=SUCCESS&analysisId=$DYNAMIC_ID&limit=20&offset=0" | jq '.' >/dev/null

echo "Smoke E2E completed successfully"
