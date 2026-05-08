"""Contract tests for the static correlation output schema.

The cases in this module verify that representative high-signal inputs map to
stable procedure relationships, risk floors, and profile selection fields used
by downstream reporting.
"""

from worker_static.analysis.correlation import build_correlation
from worker_static.domain.models import Signal


def test_correlation_prioritizes_chained_network_and_command_paths() -> None:
    """Validate command and network signals are chained in output.

    The case models dual API evidence and validates both procedures, risk floor,
    and relationship linking used by downstream reporting.

    Args:
        None.

    Returns:
        None.
    """
    signals = [
        Signal(
            id="BEHAVIOR_SYSTEM",
            kind="command_execution_function",
            severity="HIGH",
            title="Command execution API reference detected",
            evidence={"token": "system"},
        ),
        Signal(
            id="BEHAVIOR_SOCKET",
            kind="network_function",
            severity="MEDIUM",
            title="Network socket API reference detected",
            evidence={"token": "socket"},
        ),
    ]

    correlation = build_correlation(signals)

    procedure_ids = [row["procedureId"] for row in correlation["procedures"]]
    assert "PROC_COMMAND_EXEC" in procedure_ids
    assert "PROC_NETWORK_IO" in procedure_ids
    assert correlation["riskScore"] >= 60
    assert any(link.get("type") == "chains_with" for link in correlation["relationships"])


def test_correlation_uses_container_profile_when_requested() -> None:
    """Validate explicit environment profile selection is preserved.

    The case asserts output keeps the caller-provided profile without fallback
    to the default server context.

    Args:
        None.

    Returns:
        None.
    """
    signals = [
        Signal(
            id="BEHAVIOR_SETUID",
            kind="privilege_manipulation_function",
            severity="HIGH",
            title="Privilege change API reference detected",
            evidence={"token": "setuid"},
        )
    ]

    correlation = build_correlation(signals, "CONTAINER_SERVICE")

    assert correlation["environmentProfile"] == "CONTAINER_SERVICE"
