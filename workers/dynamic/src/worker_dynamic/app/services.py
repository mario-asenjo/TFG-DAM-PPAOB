"""Application service for one dynamic-analysis processing cycle.

This module coordinates pre-exploitation analysis execution by wiring ports:

- claims pending analyses from the repository,
- fetches the submitted binary from object storage,
- executes the dynamic agent runner in an isolated temporary directory,
- persists completion/failure state and generated artifacts.

It does not implement database access, storage transport, or low-level agent
execution details; those are delegated to adapters via ports.
"""

from __future__ import annotations

import logging
from hashlib import sha256
import tempfile
from pathlib import Path

from worker_dynamic.analysis.agent_runner import AgentExecutionError, DynamicAgentRunner
from worker_dynamic.domain.models import AnalysisJob, DynamicResult
from worker_dynamic.ports.repositories import AnalysisRepository
from worker_dynamic.ports.storage import BinaryStorage


class DynamicAnalysisService:
    """Orchestrate the dynamic worker claim/process/persist flow.

    Responsibility boundary: this service controls workflow sequencing and
    state transitions, while domain interpretation and infrastructure calls are
    delegated to collaborators.
    """

    def __init__(
        self,
        repository: AnalysisRepository,
        storage: BinaryStorage,
        runner: DynamicAgentRunner,
        profile: str,
        logger: logging.Logger,
    ) -> None:
        """Build the orchestration service.

        Args:
            repository: Port used to claim jobs and persist resulting state.
            storage: Port used to download inputs and upload generated artifacts.
            runner: Component that executes the dynamic-analysis agent.
            profile: Worker profile used when claiming eligible jobs.
            logger: Logger used for operational and failure messages.
        """
        self._repository = repository
        self._storage = storage
        self._runner = runner
        self._profile = profile
        self._log = logger

    def process_once(self) -> bool:
        """Attempt a single dynamic-analysis processing cycle.

        The cycle is:
        1) claim one job for this worker profile,
        2) resolve acting user id for audit-safe state transitions,
        3) download binary and execute the dynamic runner,
        4) attach trace artifact metadata when available,
        5) mark job as done or failed.

        Returns:
            ``True`` when a job was claimed (whether it finished successfully
            or failed), ``False`` when no claimable job exists.

        Raises:
            No exception is propagated intentionally. Execution and runtime
            failures are converted into failed-job states.

        Side Effects:
            Performs repository reads/writes, object storage download/upload,
            temporary filesystem writes, permission change on the local copied
            binary, and operational logging.
        """
        message = ""

        job = self._repository.claim_next_job(self._profile)
        if job is None:
            return False

        actor_user_id = job.requested_by or self._repository.resolve_system_user_id()
        self._log.info("Claimed dynamic analysis %s", job.analysis_id)

        try:
            payload = self._storage.download_binary(job)
            with tempfile.TemporaryDirectory(prefix="dynamic-run-") as workdir:
                binary_path = Path(workdir) / "target.bin"
                output_path = Path(workdir) / "result.json"
                binary_path.write_bytes(payload)
                binary_path.chmod(0o755)
                result = self._runner.run(job, binary_path, output_path)
                self._attach_trace_artifact(job, result)
            self._repository.mark_done(job, result, actor_user_id)
            self._log.info("Completed dynamic analysis %s", job.analysis_id)
        except AgentExecutionError as error:
            message = f"Dynamic agent failure: {error}"
            self._repository.mark_failed(job, message, actor_user_id)
            self._log.warning("Dynamic analysis %s failed in agent: %s", job.analysis_id, message)
        except Exception:
            message = "Dynamic worker runtime failure"
            self._repository.mark_failed(job, message, actor_user_id)
            self._log.exception("Dynamic analysis %s failed with unexpected worker error", job.analysis_id)

        return True

    def _attach_trace_artifact(self, job: AnalysisJob, result: DynamicResult) -> None:
        """Persist trace NDJSON and enrich the in-memory result payload.

        If the runner produced ``trace_ndjson``, this method uploads it as an
        artifact, registers the artifact through the repository, and injects a
        trace summary block into ``result.dynamic``.

        Args:
            job: Claimed analysis context used to derive artifact destination.
            result: Mutable execution result that may contain ``trace_ndjson``.

        Side Effects:
            Uploads bytes to object storage, creates artifact metadata through
            the repository, and mutates ``result.artifacts`` and
            ``result.dynamic`` in place.
        """
        trace_ndjson = getattr(result, "trace_ndjson", None)
        if not trace_ndjson:
            return

        trace_bytes = trace_ndjson.encode("utf-8")
        object_key = f"artifacts/dynamic-trace/{job.analysis_id}/trace.ndjson"
        self._storage.upload_bytes(job.bucket, object_key, trace_bytes, "application/x-ndjson")

        artifact = self._repository.create_artifact(
            analysis_id=job.analysis_id,
            artifact_type="DYNAMIC_TRACE",
            bucket=job.bucket,
            object_key=object_key,
            checksum_sha256=sha256(trace_bytes).hexdigest(),
            size_bytes=len(trace_bytes),
        )

        result.artifacts.append(artifact)
        result.dynamic.setdefault("filesystem", {"paths": [], "uniquePaths": 0})
        result.dynamic.setdefault("trace", {})
        result.dynamic["trace"]["artifactId"] = artifact["artifactId"]
        result.dynamic["trace"]["artifactType"] = artifact["type"]
        result.dynamic["trace"]["lineCount"] = self._trace_line_count(trace_ndjson)

    def _trace_line_count(self, trace_ndjson: str) -> int:
        """Count non-empty entries in an NDJSON trace payload.

        Args:
            trace_ndjson: Newline-delimited JSON trace text.

        Returns:
            Number of non-blank lines, used as a lightweight trace summary.
        """
        return sum(1 for line in trace_ndjson.splitlines() if line.strip())
