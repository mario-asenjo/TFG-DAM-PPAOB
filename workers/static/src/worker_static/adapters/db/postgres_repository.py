"""PostgreSQL adapter implementing static-analysis repository operations.

This module persists analysis lifecycle state, results, and audit events in
PostgreSQL. It implements infrastructure concerns only and keeps domain
decisions in worker services.
"""

from __future__ import annotations

import json
from typing import Any
from uuid import UUID

import psycopg2
from psycopg2.extras import RealDictCursor

from worker_static.config.settings import WorkerSettings
from worker_static.domain.constants import (
    STATUS_DONE,
    STATUS_FAILED,
    STATUS_PENDING,
    STATUS_RUNNING,
)
from worker_static.domain.models import AnalysisJob, AnalysisResult
from worker_static.exceptions import WorkerConfigurationError


class PostgresAnalysisRepository:
    """Persist and retrieve static-analysis execution state in PostgreSQL.

    Integration boundaries:
    - Reads/writes ``analyses``, ``analysis_results``, ``audit_events``,
      ``binaries``, ``stored_objects``, and ``users`` tables.
    - Uses transaction-scoped row locking (``FOR UPDATE SKIP LOCKED``) to
      coordinate job claiming across concurrent workers.
    """

    def __init__(self, settings: WorkerSettings) -> None:
        """Initialize the repository with DB connection settings.

        Args:
            settings: Worker configuration containing PostgreSQL host, port,
                database name, and credentials.
        """
        self._settings = settings
        self._cached_system_user_id: UUID | None = None

    def resolve_system_user_id(self) -> UUID:
        """Resolve and cache the configured audit system user identifier.

        Returns:
            UUID of the configured audit system user account.

        Raises:
            WorkerConfigurationError: If no user row matches the configured
                system email.
            psycopg2.Error: If the query cannot be executed.

        Side Effects:
            Opens a database connection, executes a read query, and memoizes
            the resolved UUID in memory for subsequent calls.
        """
        if self._cached_system_user_id is not None:
            return self._cached_system_user_id

        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT user_id FROM users WHERE lower(email) = lower(%s)",
                (self._settings.audit_system_user_email,),
            )
            row = cursor.fetchone()
            if row is None:
                raise WorkerConfigurationError(
                    "System user not found: "
                    f"{self._settings.audit_system_user_email}"
                )
            self._cached_system_user_id = row[0]
            return self._cached_system_user_id

    def claim_next_job(self, profile: str) -> AnalysisJob | None:
        """Claim the next pending analysis for a profile.

        The claim is atomic for concurrent workers due to row-level locking and
        status transition inside the same transaction.

        Args:
            profile: Analysis profile name to filter eligible pending jobs.

        Returns:
            An ``AnalysisJob`` populated with binary metadata when a job is
            claimed, otherwise ``None`` when no pending job exists.

        Raises:
            psycopg2.Error: If SQL execution, locking, or commit fails.
            WorkerConfigurationError: If a job has no requester and the
                configured fallback system user is missing.

        Side Effects:
            Updates ``analyses.status`` from pending to running, sets
            ``started_at`` when absent, clears prior error summary, writes an
            audit event row, and commits the transaction.
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
                details={
                    "profile": job.profile,
                    "from": STATUS_PENDING,
                    "to": STATUS_RUNNING,
                },
            )
            conn.commit()
            return job

    def mark_done(self, job: AnalysisJob, result: AnalysisResult, actor_user_id: UUID) -> None:
        """Persist analysis findings and mark the job as completed.

        Args:
            job: Claimed analysis job whose lifecycle is being finalized.
            result: Structured analysis output to store in
                ``analysis_results.results_json``.
            actor_user_id: User or system actor attributed in the emitted
                audit event.

        Raises:
            psycopg2.Error: If insert/update/audit SQL operations fail.
            TypeError: If ``result.to_dict()`` includes non-serializable data
                for JSON encoding.

        Side Effects:
            Upserts into ``analysis_results``, transitions ``analyses.status``
            to done, clears error summary, inserts an audit event, and commits
            the transaction.
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
                (
                    str(job.analysis_id),
                    result.schema_version,
                    json.dumps(result.to_dict()),
                ),
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
                    "findingsCount": len(result.signals),
                },
            )
            conn.commit()

    def mark_failed(self, job: AnalysisJob, error_message: str, actor_user_id: UUID) -> None:
        """Mark a claimed analysis as failed and append an audit event.

        Error details persisted in ``analyses.error_summary`` and audit payload
        are truncated to 500 characters.

        Args:
            job: Claimed analysis job that failed during processing.
            error_message: Failure description generated by worker execution.
            actor_user_id: User or system actor attributed in the emitted
                audit event.

        Raises:
            psycopg2.Error: If status update, audit insert, or commit fails.

        Side Effects:
            Updates ``analyses`` terminal state, persists truncated error
            summary, writes a failure audit event, and commits the transaction.
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
                details={
                    "profile": job.profile,
                    "from": STATUS_RUNNING,
                    "to": STATUS_FAILED,
                    "error": summary,
                },
            )
            conn.commit()

    def _connect(self):
        """Create a new PostgreSQL connection from worker settings.

        Returns:
            A live ``psycopg2`` connection object.

        Raises:
            psycopg2.OperationalError: If credentials, host, or network
                parameters prevent establishing a connection.

        Side Effects:
            Opens a TCP connection to the configured PostgreSQL server.
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
        """Map a joined SQL row into an ``AnalysisJob`` model.

        Args:
            row: Mapping produced by ``RealDictCursor`` including analysis,
                binary, and object storage columns used by the worker.

        Returns:
            Normalized job model consumed by application services.

        Raises:
            KeyError: If any required column is missing from ``row``.
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
    def _append_audit(
        cursor,
        *,
        action: str,
        result: str,
        user_id: UUID,
        analysis_id: UUID,
        binary_id: UUID,
        details: dict[str, Any],
    ) -> None:
        """Insert an audit event row inside the current DB transaction.

        Args:
            cursor: Open cursor tied to the caller-managed transaction.
            action: Audit action name persisted in ``audit_events.action``.
            result: Outcome marker persisted in ``audit_events.result``.
            user_id: Actor principal identifier associated with the event.
            analysis_id: Analysis identifier associated with the event.
            binary_id: Binary identifier associated with the event.
            details: JSON-serializable metadata for the event payload.

        Raises:
            psycopg2.Error: If the insert statement fails.
            TypeError: If ``details`` cannot be serialized as JSON.

        Side Effects:
            Writes one row to ``audit_events`` using the caller's transaction
            context; commit/rollback is delegated to the caller.
        """
        cursor.execute(
            """
            INSERT INTO audit_events(action, result, user_id, analysis_id, binary_id, details)
            VALUES (%s, %s, %s, %s, %s, %s::jsonb)
            """,
            (
                action,
                result,
                str(user_id),
                str(analysis_id),
                str(binary_id),
                json.dumps(details),
            ),
        )
