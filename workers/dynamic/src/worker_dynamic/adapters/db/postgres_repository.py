"""PostgreSQL-backed repository adapter for dynamic analysis lifecycle.

This module implements persistence operations required by the dynamic worker to
claim pending analysis jobs, persist terminal outcomes, and register generated
artifacts. It assumes the backend schema includes `analyses`, `binaries`,
`stored_objects`, `analysis_results`, `artifacts`, and `audit_events` tables.
"""

from __future__ import annotations

import json
import uuid
from typing import Any
from uuid import UUID

import psycopg2
from psycopg2.extras import RealDictCursor

from worker_dynamic.config.settings import WorkerSettings
from worker_dynamic.domain.constants import STATUS_DONE, STATUS_FAILED, STATUS_PENDING, STATUS_RUNNING
from worker_dynamic.domain.models import AnalysisJob, DynamicResult


class PostgresAnalysisRepository:
    """Persist dynamic-analysis state transitions and related records.

    The adapter owns SQL interactions for:
    - claiming one pending dynamic analysis with row-level locking,
    - persisting final structured result payloads,
    - recording failed executions with bounded error summaries,
    - inserting artifact metadata and linked object-storage references,
    - appending audit events in the same transaction as status changes.
    """

    def __init__(self, settings: WorkerSettings) -> None:
        """Initialize the repository with PostgreSQL connection settings.

        Args:
            settings: Worker configuration with DB host, port, database name,
                credentials, and the audit system user email.

        Side Effects:
            Stores settings in memory and initializes the cached system user id
            as empty until first resolution.
        """
        self._settings = settings
        self._cached_system_user_id: UUID | None = None

    def resolve_system_user_id(self) -> UUID:
        """Resolve and cache the audit system user identifier.

        The value is loaded once from the `users` table using a
        case-insensitive email lookup, then reused for later calls to reduce
        DB reads.

        Returns:
            UUID of the configured audit system user.

        Raises:
            RuntimeError: If no user exists with
                `settings.audit_system_user_email`.
            psycopg2.Error: If the query or DB connection fails.

        Side Effects:
            Opens a DB connection, executes a SELECT query, and caches the
            resolved user id in the repository instance.
        """
        if self._cached_system_user_id is not None:
            return self._cached_system_user_id

        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute("SELECT user_id FROM users WHERE lower(email)=lower(%s)", (self._settings.audit_system_user_email,))
            row = cursor.fetchone()
            if row is None:
                raise RuntimeError(f"System user not found: {self._settings.audit_system_user_email}")
            self._cached_system_user_id = row[0]
            return self._cached_system_user_id

    def claim_next_job(self, profile: str) -> AnalysisJob | None:
        """Claim the oldest pending analysis for a dynamic profile.

        The method uses `FOR UPDATE SKIP LOCKED` to safely distribute work
        across multiple workers without double-claiming the same analysis.
        When a row is claimed, status transitions from pending to running and a
        success audit event is inserted in the same transaction.

        Args:
            profile: Analysis profile to claim (for this worker, typically the
                dynamic profile configured at runtime).

        Returns:
            An `AnalysisJob` populated with analysis metadata and source object
            location, or `None` when no pending analysis is available.

        Raises:
            RuntimeError: If the analysis has no requesting user and the
                configured audit system user cannot be resolved.
            psycopg2.Error: If transaction or SQL execution fails.

        Side Effects:
            Opens a DB transaction, updates analysis status/timestamps,
            appends an audit event, and commits when complete.
        """
        with self._connect() as conn, conn.cursor(cursor_factory=RealDictCursor) as cursor:
            cursor.execute(
                """
                WITH candidate AS (
                    SELECT a.analysis_id, a.binary_id, a.requested_by, a.profile
                    FROM analyses a
                    WHERE a.status = %s
                      AND a.profile = %s
                    ORDER BY a.created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                ),
                updated AS (
                    UPDATE analyses a
                    SET status = %s,
                        started_at = COALESCE(a.started_at, NOW()),
                        error_summary = NULL
                    FROM candidate c
                    WHERE a.analysis_id = c.analysis_id
                    RETURNING a.analysis_id, a.binary_id, a.requested_by, a.profile
                )
                SELECT u.analysis_id,
                       u.binary_id,
                       u.requested_by,
                       u.profile,
                       b.original_name,
                       b.sha256,
                       b.size_bytes,
                       so.bucket,
                       so.object_key
                FROM updated u
                JOIN binaries b ON b.binary_id = u.binary_id
                JOIN stored_objects so ON so.object_id = b.object_id
                """,
                (STATUS_PENDING, profile, STATUS_RUNNING),
            )
            row = cursor.fetchone()
            if row is None:
                conn.commit()
                return None

            job = self._row_to_job(row)
            actor_user_id = job.requested_by or self.resolve_system_user_id()
            self._append_audit(
                cursor,
                action="ANALYSIS_STATUS_UPDATE",
                result="SUCCESS",
                user_id=actor_user_id,
                analysis_id=job.analysis_id,
                binary_id=job.binary_id,
                details={"profile": job.profile, "from": STATUS_PENDING, "to": STATUS_RUNNING, "mode": "dynamic"},
            )
            conn.commit()
            return job

    def mark_done(self, job: AnalysisJob, result: DynamicResult, actor_user_id: UUID) -> None:
        """Persist final analysis output and close the analysis as done.

        The JSON payload is upserted into `analysis_results` by `analysis_id`
        so retries or reruns replace older stored output.

        Args:
            job: Claimed analysis context to finalize.
            result: Structured dynamic-analysis result to serialize as JSON.
            actor_user_id: User id recorded as actor in the audit event.

        Raises:
            psycopg2.Error: If SQL execution, JSONB persistence, or commit
                fails.

        Side Effects:
            Writes/updates `analysis_results`, updates `analyses` status and
            timestamps, inserts one audit event, and commits the transaction.
        """
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO analysis_results(analysis_id, schema_version, results_json)
                VALUES (%s, %s, %s::jsonb)
                ON CONFLICT (analysis_id) DO UPDATE
                SET schema_version = EXCLUDED.schema_version,
                    results_json = EXCLUDED.results_json,
                    stored_at = NOW()
                """,
                (str(job.analysis_id), result.schema_version, json.dumps(result.to_dict())),
            )
            cursor.execute(
                """
                UPDATE analyses
                SET status = %s,
                    finished_at = NOW(),
                    error_summary = NULL
                WHERE analysis_id = %s
                """,
                (STATUS_DONE, str(job.analysis_id)),
            )
            self._append_audit(
                cursor,
                action="ANALYSIS_STATUS_UPDATE",
                result="SUCCESS",
                user_id=actor_user_id,
                analysis_id=job.analysis_id,
                binary_id=job.binary_id,
                details={
                    "profile": job.profile,
                    "from": STATUS_RUNNING,
                    "to": STATUS_DONE,
                    "schemaVersion": result.schema_version,
                    "findingsCount": result.summary.get("findingsCount", 0),
                    "riskLevel": result.summary.get("riskLevel", "UNKNOWN"),
                },
            )
            conn.commit()

    def mark_failed(self, job: AnalysisJob, error_message: str, actor_user_id: UUID) -> None:
        """Persist failed status for an analysis and append a failure audit.

        Args:
            job: Claimed analysis context being marked as failed.
            error_message: Human-readable failure message. It is truncated to
                500 characters before storage.
            actor_user_id: User id recorded as actor in the audit event.

        Raises:
            psycopg2.Error: If SQL execution or transaction commit fails.

        Side Effects:
            Updates `analyses` status, finish timestamp, and error summary,
            inserts one failure audit event, and commits the transaction.
        """
        summary = error_message[:500]
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                UPDATE analyses
                SET status = %s,
                    finished_at = NOW(),
                    error_summary = %s
                WHERE analysis_id = %s
                """,
                (STATUS_FAILED, summary, str(job.analysis_id)),
            )
            self._append_audit(
                cursor,
                action="ANALYSIS_STATUS_UPDATE",
                result="FAIL",
                user_id=actor_user_id,
                analysis_id=job.analysis_id,
                binary_id=job.binary_id,
                details={"profile": job.profile, "from": STATUS_RUNNING, "to": STATUS_FAILED, "error": summary},
            )
            conn.commit()

    def create_artifact(
        self,
        analysis_id: UUID,
        artifact_type: str,
        bucket: str,
        object_key: str,
        checksum_sha256: str,
        size_bytes: int,
    ) -> dict[str, Any]:
        """Create persistent artifact records linked to an analysis.

        The method first inserts a storage-object reference and then creates an
        artifact row that points to that object.

        Args:
            analysis_id: Analysis that owns the produced artifact.
            artifact_type: Domain artifact type label.
            bucket: Storage bucket where the artifact payload was uploaded.
            object_key: Object key/path in the bucket.
            checksum_sha256: SHA-256 checksum for artifact integrity tracking.
            size_bytes: Artifact payload size in bytes.

        Returns:
            Dictionary with artifact metadata intended for downstream result
            composition (`artifactId`, `analysisId`, `type`, `bucket`,
            `objectKey`, `sizeBytes`, and `createdAt`).

        Raises:
            psycopg2.Error: If inserts or commit fail.

        Side Effects:
            Inserts into `stored_objects` and `artifacts`, then commits the
            transaction.
        """
        with self._connect() as conn, conn.cursor(cursor_factory=RealDictCursor) as cursor:
            cursor.execute(
                """
                INSERT INTO stored_objects(provider, bucket, object_key, checksum_sha256, size_bytes)
                VALUES ('S3', %s, %s, %s, %s)
                RETURNING object_id
                """,
                (bucket, object_key, checksum_sha256, size_bytes),
            )
            object_row = cursor.fetchone()
            object_id = object_row["object_id"] if object_row else uuid.uuid4()
            cursor.execute(
                """
                INSERT INTO artifacts(analysis_id, type, object_id)
                VALUES (%s, %s, %s)
                RETURNING artifact_id, created_at
                """,
                (str(analysis_id), artifact_type, str(object_id)),
            )
            artifact_row = cursor.fetchone() or {}
            conn.commit()
            return {
                "artifactId": str(artifact_row.get("artifact_id")),
                "analysisId": str(analysis_id),
                "type": artifact_type,
                "bucket": bucket,
                "objectKey": object_key,
                "sizeBytes": size_bytes,
                "createdAt": artifact_row.get("created_at").isoformat() if artifact_row.get("created_at") else None,
            }

    def _connect(self):
        """Open a new psycopg2 connection using configured credentials.

        Returns:
            A new PostgreSQL connection object.

        Raises:
            psycopg2.Error: If the connection cannot be established.

        Side Effects:
            Initiates a network connection to the configured PostgreSQL server.
        """
        return psycopg2.connect(
            host=self._settings.db_host,
            port=self._settings.db_port,
            dbname=self._settings.db_name,
            user=self._settings.db_user,
            password=self._settings.db_password,
        )

    @staticmethod
    def _row_to_job(row: dict[str, Any]) -> AnalysisJob:
        """Map a selected database row to an `AnalysisJob` model.

        Args:
            row: Dictionary-like DB row containing all fields required by
                `AnalysisJob`.

        Returns:
            Materialized `AnalysisJob` instance.

        Raises:
            KeyError: If required columns are missing in `row`.
        """
        return AnalysisJob(
            analysis_id=row["analysis_id"],
            binary_id=row["binary_id"],
            requested_by=row["requested_by"],
            profile=row["profile"],
            original_name=row["original_name"],
            sha256=row["sha256"],
            size_bytes=row["size_bytes"],
            bucket=row["bucket"],
            object_key=row["object_key"],
        )

    @staticmethod
    def _append_audit(cursor, *, action: str, result: str, user_id: UUID, analysis_id: UUID, binary_id: UUID, details: dict[str, Any]) -> None:
        """Insert one audit event using the active transaction cursor.

        Args:
            cursor: Open DB cursor bound to the current transaction.
            action: Audit action identifier.
            result: Outcome label (for example `SUCCESS` or `FAIL`).
            user_id: Actor user identifier.
            analysis_id: Related analysis identifier.
            binary_id: Related binary identifier.
            details: JSON-serializable audit detail payload.

        Raises:
            psycopg2.Error: If the insert fails.

        Side Effects:
            Appends one row into `audit_events` in the current DB transaction.
        """
        cursor.execute(
            """
            INSERT INTO audit_events(action, result, user_id, analysis_id, binary_id, details)
            VALUES (%s, %s, %s, %s, %s, %s::jsonb)
            """,
            (action, result, str(user_id), str(analysis_id), str(binary_id), json.dumps(details)),
        )
