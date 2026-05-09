"""Regression coverage for correlation scoring calibration cases.

The suite replays curated pre-exploitation runtime observations stored in a
JSON dataset and asserts that priority, risk, and required procedures stay
within agreed bounds.
"""

from __future__ import annotations

import json
from pathlib import Path

from worker_dynamic.analysis.correlation import build_correlation


def test_dynamic_correlation_regression_cases() -> None:
    """Validate correlation outputs against the stored regression dataset.

    Args:
        None: Test inputs are loaded from ``tests/data`` fixtures.

    Returns:
        None: Assertions guarantee correlation score ranges and required
        procedure mappings remain stable across refactors.
    """

    cases_path = Path(__file__).parent / "data" / "correlation_regression_cases.json"
    cases = json.loads(cases_path.read_text(encoding="utf-8"))

    for case in cases:
        correlation = build_correlation(
            runtime=case["runtime"],
            policy=case["policy"],
            top_syscalls=case["topSyscalls"],
            signals=case["signals"],
            environment_profile=case["profile"],
            deployment_context=case.get("deploymentContext"),
            observed_runs=int(case.get("observedRuns", 1)),
        )
        expected = case["expected"]

        assert correlation["priority"] == expected["priority"], case["name"]
        assert correlation["riskScore"] >= expected["minRiskScore"], case["name"]
        assert correlation["riskScore"] <= expected["maxRiskScore"], case["name"]
        assert correlation["exploitability"]["score"] >= expected["minExploitabilityScore"], case["name"]
        assert correlation["executionEvidence"]["observedRuns"] == int(case.get("observedRuns", 1)), case["name"]

        procedure_ids = {row["procedureId"] for row in correlation["procedures"]}
        required = set(expected["requiredProcedures"])
        assert required.issubset(procedure_ids), case["name"]
