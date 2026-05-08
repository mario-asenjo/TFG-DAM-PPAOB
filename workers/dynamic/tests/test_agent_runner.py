"""Contract tests for dynamic agent runner mapping and failures.

This module validates how :class:`DynamicAgentRunner` transforms agent JSON
outputs into domain results and how it surfaces execution errors. Tests stub
``subprocess.run`` so contracts are checked without invoking the native agent.
"""

from __future__ import annotations

import json
from pathlib import Path
from uuid import uuid4

import pytest

from worker_dynamic.analysis.agent_runner import AgentExecutionError
from worker_dynamic.analysis.agent_runner import DynamicAgentRunner
from worker_dynamic.analysis.agent_runner import _compact_message
from worker_dynamic.domain.models import AnalysisJob


def _job() -> AnalysisJob:
    """Create a baseline analysis job fixture used by runner tests.

    Args:
        None: Fixture is fully deterministic for this module.

    Returns:
        AnalysisJob: Job metadata matching the baseline dynamic profile.
    """

    return AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="DYNAMIC_BASELINE",
        original_name="sample.bin",
        sha256="a" * 64,
        size_bytes=16,
        bucket="ppaob-binaries",
        object_key="binaries/sample.bin",
    )


def test_compact_message_handles_empty_and_truncates() -> None:
    """Validate normalization and truncation behavior for compact errors.

    Args:
        None: Inputs are inline literals covering edge cases.

    Returns:
        None: Assertions verify fallback behavior, newline compaction, and max
        message length applied by the helper.
    """

    assert _compact_message("\n\n", "fallback") == "fallback"
    assert _compact_message("line1\nline2", "fallback") == "line1 line2"
    assert len(_compact_message("x" * 300, "fallback")) == 220


def test_run_maps_valid_payload(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    """Ensure valid agent output is mapped into the expected result contract.

    Args:
        monkeypatch: Pytest fixture used to replace ``subprocess.run``.
        tmp_path: Temporary directory that simulates output and input files.

    Returns:
        None: Assertions validate summary, dynamic blocks, correlation defaults,
        and optional trace extraction.
    """

    output_path = tmp_path / "result.json"
    local_binary = tmp_path / "target.bin"
    local_binary.write_bytes(b"\x7fELF")

    payload = {
        "runtime": {"exitCode": 0, "timedOut": False, "durationMs": 12, "terminationReason": "NORMAL", "signalNumber": 0},
        "policy": {"name": "SECCOMP_BASELINE_V1", "deniedCount": 0},
        "topSyscalls": [{"name": "read", "count": 2}],
        "filesystem": {"uniquePaths": 1, "paths": [{"syscall": "openat", "number": 257, "path": "/tmp/a"}]},
    }

    def fake_run(*args, **kwargs):
        trace_output = Path(args[0][args[0].index("--trace-output") + 1])
        trace_output.write_text('{"seq":1,"phase":"enter","name":"openat"}\n', encoding="utf-8")
        output_path.write_text(
            json.dumps(payload),
            encoding="utf-8",
        )

        class Completed:
            returncode = 0
            stderr = ""
            stdout = ""

        return Completed()

    monkeypatch.setattr("worker_dynamic.analysis.agent_runner.subprocess.run", fake_run)

    runner = DynamicAgentRunner(binary_path="/tmp/agent_dynamic", timeout_ms=1000)
    result = runner.run(_job(), local_binary, output_path)

    assert result.summary["riskLevel"] == "LOW"
    assert result.dynamic["runtime"]["exitCode"] == 0
    assert result.dynamic["policy"]["name"] == payload["policy"]["name"]
    assert result.dynamic["topSyscalls"][0]["name"] == "read"
    assert result.dynamic["filesystem"]["uniquePaths"] == 1
    assert result.summary["terminationReason"] == "NORMAL"
    assert result.summary["signalNumber"] == 0
    assert result.trace_ndjson is not None
    assert result.signals == []
    assert result.summary["riskScore"] >= 0
    assert result.correlation["environmentProfile"] == "LINUX_SERVER"


def test_run_raises_on_non_zero_exit(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    """Ensure non-zero process exits are exposed as ``AgentExecutionError``.

    Args:
        monkeypatch: Pytest fixture used to replace ``subprocess.run``.
        tmp_path: Temporary directory that simulates local execution files.

    Returns:
        None: Assertion verifies stderr lines are compacted in the exception
        message propagated by the runner.
    """

    output_path = tmp_path / "result.json"
    local_binary = tmp_path / "target.bin"
    local_binary.write_bytes(b"\x7fELF")

    def fake_run(*args, **kwargs):
        class Completed:
            returncode = 9
            stderr = "fatal\nerror"
            stdout = ""

        return Completed()

    monkeypatch.setattr("worker_dynamic.analysis.agent_runner.subprocess.run", fake_run)

    runner = DynamicAgentRunner(binary_path="/tmp/agent_dynamic", timeout_ms=1000)
    with pytest.raises(AgentExecutionError, match="fatal error"):
        runner.run(_job(), local_binary, output_path)


def test_run_raises_when_runtime_fields_missing(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """Ensure malformed runtime payloads fail with a contract-level error.

    Args:
        monkeypatch: Pytest fixture used to replace ``subprocess.run``.
        tmp_path: Temporary directory that simulates local execution files.

    Returns:
        None: Assertion checks required runtime fields are enforced before
        producing a dynamic result.
    """

    output_path = tmp_path / "result.json"
    local_binary = tmp_path / "target.bin"
    local_binary.write_bytes(b"\x7fELF")

    def fake_run(*args, **kwargs):
        output_path.write_text("{" + '"runtime":{}' + "}", encoding="utf-8")

        class Completed:
            returncode = 0
            stderr = ""
            stdout = ""

        return Completed()

    monkeypatch.setattr("worker_dynamic.analysis.agent_runner.subprocess.run", fake_run)

    runner = DynamicAgentRunner(binary_path="/tmp/agent_dynamic", timeout_ms=1000)
    with pytest.raises(AgentExecutionError, match="missing runtime fields"):
        runner.run(_job(), local_binary, output_path)


def test_run_builds_correlation_for_network_exec_chain(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """Validate correlation escalation for command execution plus networking.

    Args:
        monkeypatch: Pytest fixture used to replace ``subprocess.run``.
        tmp_path: Temporary directory that simulates local execution files.

    Returns:
        None: Assertions verify required procedures and high-risk scoring for
        combined runtime indicators.
    """

    output_path = tmp_path / "result.json"
    local_binary = tmp_path / "target.bin"
    local_binary.write_bytes(b"\x7fELF")

    payload = {
        "runtime": {"exitCode": 124, "timedOut": True, "durationMs": 3000, "terminationReason": "TIMEOUT", "signalNumber": 9},
        "policy": {"name": "SECCOMP_BASELINE_V1", "deniedCount": 2},
        "topSyscalls": [
            {"name": "execve", "number": 59, "count": 3},
            {"name": "socket", "number": 41, "count": 5},
        ],
    }

    def fake_run(*args, **kwargs):
        output_path.write_text(json.dumps(payload), encoding="utf-8")

        class Completed:
            returncode = 0
            stderr = ""
            stdout = ""

        return Completed()

    monkeypatch.setattr("worker_dynamic.analysis.agent_runner.subprocess.run", fake_run)

    runner = DynamicAgentRunner(binary_path="/tmp/agent_dynamic", timeout_ms=1000)
    result = runner.run(_job(), local_binary, output_path)

    procedure_ids = [row["procedureId"] for row in result.correlation["procedures"]]
    assert "PROC_RUNTIME_COMMAND_EXEC" in procedure_ids
    assert "PROC_RUNTIME_NETWORK_FLOW" in procedure_ids
    assert result.summary["riskLevel"] == "HIGH"
    assert result.correlation["riskScore"] >= 75


def test_run_adds_signal_finding_for_crash(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    """Ensure crash signals are emitted as dynamic findings.

    Args:
        monkeypatch: Pytest fixture used to replace ``subprocess.run``.
        tmp_path: Temporary directory that simulates local execution files.

    Returns:
        None: Assertion verifies a signal-based finding is included for a
        signal-terminated execution.
    """

    output_path = tmp_path / "result.json"
    local_binary = tmp_path / "target.bin"
    local_binary.write_bytes(b"\x7fELF")

    payload = {
        "runtime": {
            "exitCode": 139,
            "timedOut": False,
            "durationMs": 9,
            "terminationReason": "SIGNAL",
            "signalNumber": 11,
        },
        "policy": {"name": "SECCOMP_BASELINE_V1", "deniedCount": 0},
        "topSyscalls": [{"name": "write", "number": 1, "count": 1}],
    }

    def fake_run(*args, **kwargs):
        output_path.write_text(json.dumps(payload), encoding="utf-8")

        class Completed:
            returncode = 0
            stderr = ""
            stdout = ""

        return Completed()

    monkeypatch.setattr("worker_dynamic.analysis.agent_runner.subprocess.run", fake_run)

    runner = DynamicAgentRunner(binary_path="/tmp/agent_dynamic", timeout_ms=1000)
    result = runner.run(_job(), local_binary, output_path)

    ids = [signal["id"] for signal in result.signals]
    assert "DYN_PROCESS_SIGNAL" in ids


def test_run_uses_container_profile_when_configured(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """Ensure configured environment profile is propagated to correlation.

    Args:
        monkeypatch: Pytest fixture used to replace ``subprocess.run``.
        tmp_path: Temporary directory that simulates local execution files.

    Returns:
        None: Assertion verifies runner-level profile configuration overrides
        the default environment profile in the correlation block.
    """

    output_path = tmp_path / "result.json"
    local_binary = tmp_path / "target.bin"
    local_binary.write_bytes(b"\x7fELF")

    payload = {
        "runtime": {"exitCode": 0, "timedOut": False, "durationMs": 20},
        "policy": {"name": "SECCOMP_BASELINE_V1", "deniedCount": 0},
        "topSyscalls": [{"name": "setuid", "number": 105, "count": 1}],
    }

    def fake_run(*args, **kwargs):
        output_path.write_text(json.dumps(payload), encoding="utf-8")

        class Completed:
            returncode = 0
            stderr = ""
            stdout = ""

        return Completed()

    monkeypatch.setattr("worker_dynamic.analysis.agent_runner.subprocess.run", fake_run)

    runner = DynamicAgentRunner(
        binary_path="/tmp/agent_dynamic",
        timeout_ms=1000,
        correlation_environment_profile="CONTAINER_SERVICE",
    )
    result = runner.run(_job(), local_binary, output_path)

    assert result.correlation["environmentProfile"] == "CONTAINER_SERVICE"
