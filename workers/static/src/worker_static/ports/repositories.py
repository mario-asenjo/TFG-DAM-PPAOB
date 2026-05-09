"""Persistence contracts for static worker analysis lifecycle.

This module defines the repository boundary used by application services to
claim work and persist terminal outcomes. Implementations own transaction,
locking, schema, and audit-storage details while preserving these contracts.
"""

from __future__ import annotations

from typing import Protocol
from uuid import UUID

from worker_static.domain.models import AnalysisJob, AnalysisResult


class AnalysisRepository(Protocol):
    """Contract for analysis lifecycle state and result persistence.

    The service layer relies on this port to enforce processing order:
    claim pending work, execute analysis, then persist done/failed terminal
    transitions.
    """

    def resolve_system_user_id(self) -> UUID:
        """Resolve the fallback actor identity used for auditing.

        Returns:
            UUID: Stable identifier representing the configured system actor.

        Raises:
            Exception: Adapter-specific persistence/configuration failures if
                the fallback identity cannot be resolved.

        Side Effects:
            May query persistent storage and may cache the resolved value in
            process memory.
        """

    def claim_next_job(self, profile: str) -> AnalysisJob | None:
        """Atomically claim one pending analysis for the selected profile.

        Preconditions:
            ``profile`` identifies the queue segment to poll.

        Postconditions:
            - Returns ``None`` when no pending job is available.
            - Returns a fully populated ``AnalysisJob`` when a claim succeeds.
            - Claimed jobs are transitioned to an in-progress/running state so
              concurrent workers do not process the same analysis.

        Args:
            profile: Analysis profile key that scopes eligible pending jobs.

        Returns:
            AnalysisJob | None: Claimed job with binary/storage metadata, or
            ``None`` if no claimable job exists.

        Raises:
            Exception: Adapter-specific transaction, locking, or query errors.

        Side Effects:
            Reads and updates persistent lifecycle state and may append audit
            records as part of the same logical claim operation.
        """

    def mark_done(self, job: AnalysisJob, result: AnalysisResult, actor_user_id: UUID) -> None:
        """Persist a successful analysis output and terminal done status.

        Preconditions:
            ``job`` has previously been claimed and corresponds to the
            processing unit that produced ``result``.

        Postconditions:
            Analysis lifecycle state is marked as completed and persisted output
            is available for downstream consumers.

        Args:
            job: Claimed analysis job being finalized.
            result: Structured static-analysis output to persist.
            actor_user_id: Identity attributed to audit/lifecycle updates.

        Returns:
            None.

        Raises:
            Exception: Adapter-specific write/serialization/transaction errors.

        Side Effects:
            Writes result payloads, updates analysis status fields, and records
            delegated audit trail entries in persistence.
        """

    def mark_failed(self, job: AnalysisJob, error_message: str, actor_user_id: UUID) -> None:
        """Persist a terminal failed status for a claimed analysis job.

        Preconditions:
            ``job`` has been claimed for execution and cannot be completed due
            to a runtime or dependency failure.

        Postconditions:
            Analysis lifecycle state is marked as failed and the provided
            failure detail is persisted according to adapter constraints.

        Args:
            job: Claimed analysis job being transitioned to failed.
            error_message: Human-readable failure detail produced by runtime
                orchestration.
            actor_user_id: Identity attributed to audit/lifecycle updates.

        Returns:
            None.

        Raises:
            Exception: Adapter-specific write/transaction failures.

        Side Effects:
            Updates persistent analysis status/error fields and records
            delegated failure-audit metadata.
        """
