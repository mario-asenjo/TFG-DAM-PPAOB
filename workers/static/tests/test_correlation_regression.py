"""Regression tests for static correlation calibration datasets.

The suite executes JSON-defined scenarios to keep scoring and prioritization
stable across refactors in the static pre-exploitation correlation pipeline.
"""

from __future__ import annotations

import json
from pathlib import Path

from worker_static.analysis.correlation import build_correlation
from worker_static.domain.models import Signal


def test_static_correlation_regression_cases() -> None:
    """Run dataset-driven correlation regression scenarios.

    The fixture defines normalized inputs and expected windows. The contract is
    stable priority, score bounds, observed-run propagation, and required
    procedures across refactors.

    Args:
        None.

    Returns:
        None.
    """
    cases_path = Path(__file__).parent / "data" / "correlation_regression_cases.json"
    cases = json.loads(cases_path.read_text(encoding="utf-8"))

    for case in cases:
        signals = [
            Signal(
                id=row["id"],
                kind=row["kind"],
                severity=row["severity"],
                title=row["title"],
                evidence=row.get("evidence", {}),
            )
            for row in case["signals"]
        ]

        correlation = build_correlation(
            signals,
            case["profile"],
            case.get("deploymentContext"),
            int(case.get("observedRuns", 1)),
        )
        expected = case["expected"]

        assert correlation["priority"] == expected["priority"], case["name"]
        assert correlation["riskScore"] >= expected["minRiskScore"], case["name"]
        assert correlation["riskScore"] <= expected["maxRiskScore"], case["name"]
        assert (
            correlation["exploitability"]["score"]
            >= expected["minExploitabilityScore"]
        ), case["name"]
        assert (
            correlation["executionEvidence"]["observedRuns"]
            == int(case.get("observedRuns", 1))
        ), case["name"]

        procedure_ids = {row["procedureId"] for row in correlation["procedures"]}
        required = set(expected["requiredProcedures"])
        assert required.issubset(procedure_ids), case["name"]
