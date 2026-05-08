"""Repository contracts used by dynamic-worker application services.

The protocol in this module defines persistence responsibilities for the
analysis lifecycle: claim pending work, persist terminal outcomes, and register
artifact metadata. Concrete adapters own transaction handling, SQL details, and
audit/event side effects while preserving these observable contracts.
"""

from __future__ import annotations

from typing import Protocol
from uuid import UUID

from worker_dynamic.domain.models import AnalysisJob, DynamicResult


class AnalysisRepository(Protocol):
    """Persistence contract for dynamic-analysis state transitions.

    Implementations are expected to provide atomic and durable writes for
    lifecycle updates so services can treat each method call as a single
    repository operation from the application perspective.
    """

    def resolve_system_user_id(self) -> UUID:
        """Resolve the configured audit/system user identifier.

        Returns:
            UUID of the user used as actor when a job lacks `requested_by`.

        Raises:
            RuntimeError: If the configured user cannot be resolved.
            Exception: Adapter-specific persistence or connectivity errors.

        Side Effects:
            May perform one or more external persistence reads and cache results
            in adapter state.
        """

    def claim_next_job(self, profile: str) -> AnalysisJob | None:
        """Claim one pending analysis job for the requested profile.

        Implementations should prevent concurrent double-claiming across worker
        instances (for example, with row/object locking primitives) and should
        transition the claimed job into an in-progress state before returning.

        Args:
            profile: Profile name the worker is currently responsible for.

        Returns:
            The claimed `AnalysisJob` when available, otherwise `None` when no
            pending job matches `profile`.

        Raises:
            RuntimeError: If required actor metadata cannot be resolved.
            Exception: Adapter-specific persistence or connectivity errors.

        Side Effects:
            Reads and writes persistent job state; may append audit metadata in
            the same unit of work.
        """

    def mark_done(self, job: AnalysisJob, result: DynamicResult, actor_user_id: UUID) -> None:
        """Persist final analysis output and mark the job as completed.

        Args:
            job: Previously claimed analysis job being finalized.
            result: Structured dynamic-analysis result to persist.
            actor_user_id: User id to attribute in related audit events.

        Raises:
            Exception: Adapter-specific persistence or connectivity errors.

        Side Effects:
            Writes persistent result/state records and emits delegated audit or
            traceability records required by the backend data model.
        """

    def mark_failed(self, job: AnalysisJob, error_message: str, actor_user_id: UUID) -> None:
        """Persist a terminal failed state for a claimed job.

        Args:
            job: Previously claimed analysis job being finalized as failed.
            error_message: Human-readable failure summary for operators.
            actor_user_id: User id to attribute in related audit events.

        Raises:
            Exception: Adapter-specific persistence or connectivity errors.

        Side Effects:
            Updates persistent status/error fields and emits delegated audit or
            traceability records.
        """

    def create_artifact(
        self,
        analysis_id: UUID,
        artifact_type: str,
        bucket: str,
        object_key: str,
        checksum_sha256: str,
        size_bytes: int,
    ) -> dict:
        """Register metadata for an already-uploaded artifact payload.

        Args:
            analysis_id: Analysis identifier that owns the artifact.
            artifact_type: Domain artifact type label.
            bucket: Object-storage bucket where payload is stored.
            object_key: Object-storage key/path of the payload.
            checksum_sha256: SHA-256 checksum for payload integrity tracking.
            size_bytes: Payload size in bytes.

        Returns:
            Dictionary with artifact metadata required by application services.
            Keys are adapter-defined but should include stable identifiers and
            storage location fields needed for downstream serialization.

        Raises:
            Exception: Adapter-specific persistence or connectivity errors.

        Side Effects:
            Creates persistent metadata records (and possibly linked storage
            object references) in external data stores.
        """
