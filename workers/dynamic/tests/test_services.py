"""Service-level tests for dynamic job processing contracts.

These tests isolate ``DynamicAnalysisService.process_once`` with in-memory
fakes to validate success/failure transitions, artifact persistence behavior,
and error summaries written to the repository contract.
"""

from __future__ import annotations

import logging
from pathlib import Path
from uuid import uuid4

from worker_dynamic.analysis.agent_runner import AgentExecutionError
from worker_dynamic.app.services import DynamicAnalysisService
from worker_dynamic.domain.models import AnalysisJob
from worker_dynamic.domain.models import DynamicResult


class FakeRepository:
    """In-memory repository double used to observe service side effects."""

    def __init__(self, job: AnalysisJob | None) -> None:
        """Initialize repository state for one optional pending job.

        Args:
            job: Job returned by ``claim_next_job`` or ``None`` when the queue
                is intentionally empty.

        Returns:
            None: Initializes call tracking lists.
        """

        self.job = job
        self.done_calls: list[tuple[AnalysisJob, DynamicResult, object]] = []
        self.failed_calls: list[tuple[AnalysisJob, str, object]] = []
        self.artifact_calls: list[dict] = []

    def claim_next_job(self, profile: str) -> AnalysisJob | None:
        """Return the configured job regardless of profile.

        Args:
            profile: Requested analysis profile from the service.

        Returns:
            AnalysisJob | None: Pending job fixture or ``None``.
        """

        return self.job

    def resolve_system_user_id(self):
        """Provide a synthetic actor identity for state transitions.

        Returns:
            UUID: Random identifier representing the system actor.
        """

        return uuid4()

    def mark_done(
        self, job: AnalysisJob, result: DynamicResult, actor_user_id
    ) -> None:
        """Record successful completion calls made by the service.

        Args:
            job: Claimed analysis job.
            result: Dynamic analysis result persisted as done state.
            actor_user_id: Identifier returned by ``resolve_system_user_id``.

        Returns:
            None: Appends invocation payload to ``done_calls``.
        """

        self.done_calls.append((job, result, actor_user_id))

    def mark_failed(
        self, job: AnalysisJob, error_summary: str, actor_user_id
    ) -> None:
        """Record failed completion calls made by the service.

        Args:
            job: Claimed analysis job.
            error_summary: User-facing failure summary persisted by service.
            actor_user_id: Identifier returned by ``resolve_system_user_id``.

        Returns:
            None: Appends invocation payload to ``failed_calls``.
        """

        self.failed_calls.append((job, error_summary, actor_user_id))

    def create_artifact(
        self,
        analysis_id,
        artifact_type,
        bucket,
        object_key,
        checksum_sha256,
        size_bytes,
    ) -> dict:
        """Record artifact metadata and return a synthetic artifact row.

        Args:
            analysis_id: Identifier of the owning analysis.
            artifact_type: Logical type for stored artifact.
            bucket: Object storage bucket name.
            object_key: Object storage key.
            checksum_sha256: SHA-256 checksum of uploaded bytes.
            size_bytes: Artifact size in bytes.

        Returns:
            dict: Metadata row shaped like repository artifact responses.
        """

        record = {
            "analysisId": str(analysis_id),
            "type": artifact_type,
            "bucket": bucket,
            "objectKey": object_key,
            "checksum": checksum_sha256,
            "sizeBytes": size_bytes,
            "artifactId": str(uuid4()),
        }
        self.artifact_calls.append(record)
        return record


class FakeStorage:
    """Storage double for deterministic download/upload behavior."""

    def __init__(self, payload: bytes, should_fail: bool = False) -> None:
        """Configure storage behavior for a test scenario.

        Args:
            payload: Bytes returned by ``download_binary`` when successful.
            should_fail: Whether ``download_binary`` should raise runtime error.

        Returns:
            None: Initializes upload tracking state.
        """

        self.payload = payload
        self.should_fail = should_fail
        self.uploaded: list[tuple[str, str, bytes, str]] = []

    def download_binary(self, job: AnalysisJob) -> bytes:
        """Return configured bytes or raise to simulate backend outage.

        Args:
            job: Analysis job being downloaded.

        Returns:
            bytes: Binary payload used by the dynamic runner.

        Raises:
            RuntimeError: If ``should_fail`` is enabled.
        """

        if self.should_fail:
            raise RuntimeError("storage down")
        return self.payload

    def upload_bytes(self, bucket: str, object_key: str, payload: bytes, content_type: str) -> None:
        """Record artifact uploads requested by the service.

        Args:
            bucket: Destination bucket name.
            object_key: Destination object key.
            payload: Bytes uploaded by service.
            content_type: MIME type associated with uploaded payload.

        Returns:
            None: Appends upload data to ``uploaded`` for assertions.
        """

        self.uploaded.append((bucket, object_key, payload, content_type))


class FakeRunner:
    """Runner double that returns a configured result or raises."""

    def __init__(
        self, result: DynamicResult | None = None, exc: Exception | None = None
    ) -> None:
        """Initialize dynamic runner outcome for a test path.

        Args:
            result: Result returned when execution succeeds.
            exc: Exception raised when execution should fail.

        Returns:
            None: Stores deterministic behavior used by ``run``.
        """

        self.result = result
        self.exc = exc

    def run(
        self, job: AnalysisJob, local_binary_path: Path, output_path: Path
    ) -> DynamicResult:
        """Execute configured fake behavior for the service under test.

        Args:
            job: Analysis job being processed.
            local_binary_path: Temporary path where binary is written.
            output_path: Destination JSON output path for runner contract.

        Returns:
            DynamicResult: Preconfigured dynamic result fixture.

        Raises:
            Exception: Re-raises configured exception to drive failure paths.
            RuntimeError: If no result or exception was configured.
        """

        if self.exc is not None:
            raise self.exc
        if self.result is None:
            raise RuntimeError("test runner result not configured")
        return self.result


def _job() -> AnalysisJob:
    """Create a baseline job fixture used by service tests.

    Args:
        None: Fixture values are constant defaults for test readability.

    Returns:
        AnalysisJob: Job metadata for ``DYNAMIC_BASELINE`` processing.
    """

    return AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="DYNAMIC_BASELINE",
        original_name="sample.bin",
        sha256="a" * 64,
        size_bytes=20,
        bucket="ppaob-binaries",
        object_key="binaries/sample.bin",
    )


def _result(trace_ndjson: str | None = None) -> DynamicResult:
    """Create a minimal valid dynamic result fixture.

    Args:
        trace_ndjson: Optional trace payload included to test artifact flow.

    Returns:
        DynamicResult: Result object that satisfies service persistence
        contracts.
    """

    return DynamicResult(
        schema_version=1,
        profile="DYNAMIC_BASELINE",
        metadata={
            "analysisId": str(uuid4()),
            "binaryId": str(uuid4()),
            "requestedProfile": "DYNAMIC_BASELINE",
            "producer": "worker-dynamic",
        },
        file_info={
            "binaryId": str(uuid4()),
            "originalName": "sample.bin",
            "sha256": "a" * 64,
            "sizeBytes": 20,
        },
        summary={
            "riskLevel": "LOW",
            "findingsCount": 0,
            "topSyscall": "none",
            "exitCode": 0,
        },
        static={},
        dynamic={
            "runtime": {"exitCode": 0, "timedOut": False, "durationMs": 10},
            "policy": {"name": "SECCOMP_BASELINE_V1", "deniedCount": 0},
            "topSyscalls": [],
        },
        signals=[],
        correlation={
            "version": "S09_CORR_V1",
            "environmentProfile": "LINUX_SERVER",
            "riskScore": 10,
            "priority": "P3",
            "procedures": [],
            "relationships": [],
            "topReasons": [],
        },
        artifacts=[],
        trace_ndjson=trace_ndjson,
    )


def test_process_once_returns_false_when_no_job() -> None:
    """Verify idle polling returns ``False`` without side effects.

    Args:
        None: Scenario setup is fully local to this test.

    Returns:
        None: Assertion validates the service reports no work when queue is
        empty.
    """

    service = DynamicAnalysisService(
        repository=FakeRepository(job=None),
        storage=FakeStorage(payload=b""),
        runner=FakeRunner(result=_result()),
        profile="DYNAMIC_BASELINE",
        logger=logging.getLogger("test"),
    )

    assert service.process_once() is False


def test_process_once_marks_done_on_success() -> None:
    """Verify successful execution records done state and no failures.

    Args:
        None: Scenario setup is fully local to this test.

    Returns:
        None: Assertions confirm one done transition and zero failed
        transitions.
    """

    repo = FakeRepository(job=_job())
    service = DynamicAnalysisService(
        repository=repo,
        storage=FakeStorage(payload=b"\x7fELF"),
        runner=FakeRunner(result=_result()),
        profile="DYNAMIC_BASELINE",
        logger=logging.getLogger("test"),
    )

    assert service.process_once() is True
    assert len(repo.done_calls) == 1
    assert len(repo.failed_calls) == 0


def test_process_once_marks_failed_on_agent_error() -> None:
    """Verify known agent failures are persisted with specific summaries.

    Args:
        None: Scenario setup is fully local to this test.

    Returns:
        None: Assertions confirm failed transition and expected
        ``AgentExecutionError`` message prefix.
    """

    repo = FakeRepository(job=_job())
    service = DynamicAnalysisService(
        repository=repo,
        storage=FakeStorage(payload=b"\x7fELF"),
        runner=FakeRunner(exc=AgentExecutionError("bad payload")),
        profile="DYNAMIC_BASELINE",
        logger=logging.getLogger("test"),
    )

    assert service.process_once() is True
    assert len(repo.done_calls) == 0
    assert len(repo.failed_calls) == 1
    assert repo.failed_calls[0][1] == "Dynamic agent failure: bad payload"


def test_process_once_marks_failed_on_unexpected_runtime_error() -> None:
    """Verify unexpected runtime errors use generic failure summary.

    Args:
        None: Scenario setup is fully local to this test.

    Returns:
        None: Assertions confirm service catches non-agent exceptions and marks
        job as failed.
    """

    repo = FakeRepository(job=_job())
    service = DynamicAnalysisService(
        repository=repo,
        storage=FakeStorage(payload=b"\x7fELF", should_fail=True),
        runner=FakeRunner(result=_result()),
        profile="DYNAMIC_BASELINE",
        logger=logging.getLogger("test"),
    )

    assert service.process_once() is True
    assert len(repo.done_calls) == 0
    assert len(repo.failed_calls) == 1
    assert repo.failed_calls[0][1] == "Dynamic worker runtime failure"


def test_process_once_uploads_trace_artifact_when_present() -> None:
    """Verify trace payloads are uploaded and linked as artifacts.

    Args:
        None: Scenario setup is fully local to this test.

    Returns:
        None: Assertions validate upload invocation, artifact record creation,
        and trace metadata injected into stored dynamic result.
    """

    repo = FakeRepository(job=_job())
    storage = FakeStorage(payload=b"\x7fELF")
    result = _result('{"seq":1,"name":"openat"}\n')
    service = DynamicAnalysisService(
        repository=repo,
        storage=storage,
        runner=FakeRunner(result=result),
        profile="DYNAMIC_BASELINE",
        logger=logging.getLogger("test"),
    )

    assert service.process_once() is True
    assert len(storage.uploaded) == 1
    assert len(repo.artifact_calls) == 1
    done_result = repo.done_calls[0][1]
    assert done_result.dynamic["trace"]["artifactType"] == "DYNAMIC_TRACE"
