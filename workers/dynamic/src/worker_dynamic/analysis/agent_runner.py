"""Agent invocation and normalization for dynamic-worker analysis output.

This module executes the dynamic C agent, validates its JSON contract, and
maps raw runtime telemetry into worker-domain structures used by persistence
and correlation.
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

from worker_dynamic.analysis.correlation import build_correlation
from worker_dynamic.analysis.correlation import resolve_risk_level_from_score
from worker_dynamic.domain.models import AnalysisJob, DynamicResult


class AgentExecutionError(RuntimeError):
    """Raised when the C agent cannot produce a valid dynamic result."""


def _compact_message(raw: str, fallback: str) -> str:
    """Build a short single-line error message for DB/audit storage."""
    text = (raw or "").strip().replace("\n", " ")
    if not text:
        return fallback
    return text[:220]


class DynamicAgentRunner:
    """Run the dynamic C agent and produce `DynamicResult` payloads.

    The runner enforces minimum payload validity, normalizes findings into
    stable signal identifiers, and computes correlation metadata for triage.
    """

    def __init__(
        self,
        binary_path: str,
        timeout_ms: int,
        correlation_environment_profile: str = "LINUX_SERVER",
        deployment_context: dict | None = None,
        observed_runs: int = 1,
    ) -> None:
        """Configure execution and correlation assumptions.

        Args:
            binary_path: Filesystem path to the dynamic C agent executable.
            timeout_ms: Agent timeout budget in milliseconds.
            correlation_environment_profile: Correlation profile identifier
                consumed by scoring logic.
            deployment_context: Optional deployment assumptions that tune
                scoring (`exposure`, `privilegeLevel`, `dataSensitivity`).
            observed_runs: Number of observed executions represented by the
                result. Values lower than 1 are normalized to 1.
        """
        self._binary_path = binary_path
        self._timeout_seconds = max(1, timeout_ms // 1000)
        self._timeout_ms = timeout_ms
        self._correlation_environment_profile = correlation_environment_profile
        self._deployment_context = deployment_context or {}
        self._observed_runs = max(1, int(observed_runs))

    def run(self, job: AnalysisJob, local_binary_path: Path, output_path: Path) -> DynamicResult:
        """Execute the agent, validate output, and map to domain result.

        Args:
            job: Claimed analysis context used to populate result metadata.
            local_binary_path: Downloaded binary path passed to the C agent.
            output_path: JSON output path expected from the C agent.

        Returns:
            `DynamicResult` ready for repository persistence.

        Raises:
            AgentExecutionError: If execution fails, output JSON is missing or
                invalid, or required runtime fields are absent.
            subprocess.TimeoutExpired: If the subprocess exceeds the enforced
                timeout window.

        Side Effects:
            Spawns a subprocess, reads generated JSON/NDJSON files, and may
            consume large trace content into memory for persistence.
        """
        trace_output_path = output_path.with_suffix(".ndjson")
        command = [
            self._binary_path,
            "--input",
            str(local_binary_path),
            "--output",
            str(output_path),
            "--trace-output",
            str(trace_output_path),
            "--timeout-ms",
            str(self._timeout_ms),
        ]
        completed = subprocess.run(command, check=False, timeout=self._timeout_seconds + 2, capture_output=True, text=True)
        if completed.returncode != 0:
            message = _compact_message(
                completed.stderr or completed.stdout,
                f"Agent exited with code {completed.returncode}",
            )
            raise AgentExecutionError(message)

        if not output_path.exists():
            raise AgentExecutionError("Agent did not produce result payload")

        try:
            raw = json.loads(output_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise AgentExecutionError("Agent produced invalid JSON payload") from exc

        runtime = raw.get("runtime", {})
        if "exitCode" not in runtime or "timedOut" not in runtime:
            raise AgentExecutionError("Agent result payload missing runtime fields")

        top_syscalls = raw.get("topSyscalls", [])
        policy = raw.get("policy", {"name": "SECCOMP_BASELINE_V1", "deniedCount": 0})
        filesystem = raw.get("filesystem", {"paths": [], "uniquePaths": 0})
        trace_ndjson = trace_output_path.read_text(encoding="utf-8") if trace_output_path.exists() else ""
        signals = self._build_signals(raw)
        correlation = build_correlation(
            runtime,
            policy,
            top_syscalls,
            signals,
            self._correlation_environment_profile,
            self._deployment_context,
            self._observed_runs,
        )
        summary = self._build_summary(
            runtime,
            top_syscalls,
            signals,
            int(correlation.get("riskScore", 0)),
        )

        return DynamicResult(
            schema_version=1,
            profile=job.profile,
            metadata={
                "analysisId": str(job.analysis_id),
                "binaryId": str(job.binary_id),
                "requestedProfile": job.profile,
                "producer": "worker-dynamic",
            },
            file_info={
                "binaryId": str(job.binary_id),
                "originalName": job.original_name,
                "sha256": job.sha256,
                "sizeBytes": job.size_bytes,
            },
            summary=summary,
            static={},
            dynamic={
                "runtime": runtime,
                "policy": policy,
                "topSyscalls": top_syscalls,
                "filesystem": filesystem,
            },
            signals=signals,
            correlation=correlation,
            artifacts=[],
            trace_ndjson=trace_ndjson,
        )

    @staticmethod
    def _build_signals(raw: dict) -> list[dict]:
        """Create normalized findings from raw runtime/policy telemetry.

        Args:
            raw: Parsed JSON payload emitted by the dynamic C agent.

        Returns:
            List of stable signal records used by triage and correlation.
        """
        runtime = raw.get("runtime", {})
        policy = raw.get("policy", {})
        findings: list[dict] = []

        if runtime.get("timedOut") is True:
            findings.append(
                {
                    "id": "DYN_TIMEOUT",
                    "kind": "EXECUTION_TIMEOUT",
                    "severity": "HIGH",
                    "title": "Execution exceeded configured timeout",
                    "evidence": {"durationMs": runtime.get("durationMs", 0)},
                }
            )
        signal_number = int(runtime.get("signalNumber", 0) or 0)
        if signal_number > 0 and runtime.get("terminationReason") == "SIGNAL":
            severity = "HIGH" if signal_number in {11, 6, 4, 7, 8} else "MEDIUM"
            findings.append(
                {
                    "id": "DYN_PROCESS_SIGNAL",
                    "kind": "PROCESS_TERMINATED_BY_SIGNAL",
                    "severity": severity,
                    "title": "Execution terminated by signal",
                    "evidence": {
                        "signalNumber": signal_number,
                        "terminationReason": runtime.get("terminationReason"),
                    },
                }
            )
        if int(policy.get("deniedCount", 0)) > 0:
            findings.append(
                {
                    "id": "DYN_SECCOMP_DENY",
                    "kind": "SECCOMP_POLICY_DENY",
                    "severity": "MEDIUM",
                    "title": "Execution attempted blocked syscall(s)",
                    "evidence": {"deniedCount": int(policy.get("deniedCount", 0))},
                }
            )
        return findings

    def _build_summary(
        self,
        runtime: dict,
        top_syscalls: list[dict],
        signals: list[dict],
        risk_score: int,
    ) -> dict:
        """Build compact risk summary persisted in `results_json`.

        Args:
            runtime: Runtime block returned by the dynamic C agent.
            top_syscalls: Ranked syscall list returned by the agent.
            signals: Normalized findings derived from runtime/policy evidence.
            risk_score: Numeric risk score computed by correlation.

        Returns:
            Summary dictionary with risk level, counts, and key runtime facts.

        Notes:
            When the profile-derived score maps to `LOW`, summary risk may be
            escalated to `MEDIUM`/`HIGH` based on signal severity so the UI does
            not hide significant runtime failures.
        """
        risk = resolve_risk_level_from_score(
            risk_score,
            self._correlation_environment_profile,
        )
        if risk == "LOW" and any(signal.get("severity") == "HIGH" for signal in signals):
            risk = "HIGH"
        elif risk == "LOW" and any(signal.get("severity") == "MEDIUM" for signal in signals):
            risk = "MEDIUM"

        return {
            "riskLevel": risk,
            "findingsCount": len(signals),
            "riskScore": risk_score,
            "topSyscall": top_syscalls[0]["name"] if top_syscalls else "none",
            "exitCode": runtime.get("exitCode", -1),
            "terminationReason": runtime.get("terminationReason", "UNKNOWN"),
            "signalNumber": runtime.get("signalNumber", 0),
        }
