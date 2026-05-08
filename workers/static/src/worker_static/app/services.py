"""Application service contracts for static worker job processing.

This module defines the orchestration service used by the worker runtime loop.
It composes repository/storage/analyzer dependencies and executes one bounded
unit of work per call, leaving polling, process lifetime, and retry cadence to
the runner layer.
"""

from __future__ import annotations

import logging

from worker_static.analysis.elf_analyzer import BaselineElfAnalyzer
from worker_static.ports.repositories import AnalysisRepository
from worker_static.ports.storage import BinaryStorage


class StaticAnalysisService:
    """Orchestrates one static-analysis job lifecycle at a time.

    The service is responsible for claim/execute/persist transitions and for
    translating unexpected runtime failures into a failed analysis state. It
    does not implement storage protocols, database access, or analysis rules;
    those concerns are delegated through injected ports.
    """

    def __init__(
        self,
        repository: AnalysisRepository,
        storage: BinaryStorage,
        analyzer: BaselineElfAnalyzer,
        profile: str,
        logger: logging.Logger,
    ) -> None:
        """Create the app service with explicit orchestration dependencies.

        Args:
            repository: Port used to claim jobs and persist terminal state
                transitions (done/failed).
            storage: Port used to obtain the binary payload associated with a
                claimed analysis job.
            analyzer: Stateless/static analyzer entry point that derives
                findings from the downloaded payload.
            profile: Deployment profile key used when claiming the next job.
            logger: Logger used for operational lifecycle events and errors.
        """
        self._repository = repository
        self._storage = storage
        self._analyzer = analyzer
        self._profile = profile
        self._log = logger

    def process_once(self) -> bool:
        """Process at most one queued job for the configured profile.

        The method claims one job, resolves the effective actor identity used
        for state transitions, executes payload analysis, and stores either the
        successful result or a failure message.

        Returns:
            bool: ``True`` when a job was claimed (even if analysis fails and
            is marked as failed), or ``False`` when no job is currently
            available for the profile.

        Raises:
            Exception: Propagates repository/storage exceptions that occur
                before a job can be marked failed (for example during initial
                claim). Exceptions raised after a claim are absorbed and
                converted into ``mark_failed`` transitions.

        Side Effects:
            Reads and writes analysis-job state through the repository port,
            downloads binary payload bytes through the storage port, and emits
            informational/exception logs.
        """
        actor_user_id = None
        message = ""

        job = self._repository.claim_next_job(self._profile)
        if job is None:
            return False

        actor_user_id = job.requested_by or self._repository.resolve_system_user_id()
        self._log.info(
            "Claimed analysis %s for binary %s",
            job.analysis_id,
            job.binary_id,
        )

        try:
            payload = self._storage.download_binary(job)
            result = self._analyzer.analyze(job, payload)
            self._repository.mark_done(job, result, actor_user_id)
            self._log.info(
                "Completed analysis %s with %s findings",
                job.analysis_id,
                len(result.signals),
            )
        except Exception as error:
            message = str(error) or "Static worker runtime failure"
            self._repository.mark_failed(job, message, actor_user_id)
            self._log.exception("Analysis %s failed: %s", job.analysis_id, message)

        return True
