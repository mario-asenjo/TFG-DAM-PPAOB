"""Immutable domain models exchanged across the static worker pipeline.

The models in this module encode analysis lifecycle payloads and result
serialization contracts. They are transport-friendly structures without direct
I/O behavior.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Any
from uuid import UUID


@dataclass(frozen=True)
class AnalysisJob:
    """Represents a single static-analysis task claimed for processing.

    Field values map to persisted job/binary metadata resolved by repository
    adapters before analysis execution starts.

    Attributes:
        analysis_id: Stable analysis identifier used to persist final results.
        binary_id: Identifier of the binary sample associated to the analysis.
        requested_by: Optional user identifier that requested the analysis.
        profile: Analysis profile key controlling static rules/correlation.
        original_name: Original filename provided at upload time.
        sha256: Hex-encoded SHA-256 digest of the binary content.
        size_bytes: Binary size in bytes.
        bucket: Object-storage bucket containing the binary artifact.
        object_key: Object-storage key/path for the binary artifact.
    """

    analysis_id: UUID
    binary_id: UUID
    requested_by: UUID | None
    profile: str
    original_name: str
    sha256: str
    size_bytes: int
    bucket: str
    object_key: str


@dataclass(frozen=True)
class Signal:
    """Represents one normalized static-analysis finding.

    Attributes:
        id: Deterministic or rule-generated finding identifier.
        kind: Finding category used for aggregation/correlation.
        severity: Normalized severity label (for example HIGH/MEDIUM/LOW).
        title: Human-readable short finding description.
        evidence: JSON-serializable evidence payload backing the finding.
    """

    id: str
    kind: str
    severity: str
    title: str
    evidence: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        """Serializes the signal into a JSON-ready dictionary.

        Returns:
            dict[str, Any]: Dataclass fields converted to plain Python types.

        Side Effects:
            None.
        """
        return asdict(self)


@dataclass(frozen=True)
class AnalysisResult:
    """Represents the full persisted output produced by static analysis.

    The object stores both structured summary sections and per-signal findings.
    ``to_dict`` applies output key normalization required by persistence/API
    consumers (camelCase keys and generated timestamp metadata).

    Attributes:
        schema_version: Output schema version for compatibility checks.
        profile: Effective analysis profile used to produce this result.
        summary: High-level analysis counters and aggregate indicators.
        file_info: Metadata describing the analyzed artifact.
        metadata: Additional run metadata merged with generated timestamp.
        static: Static-analysis engine output payload.
        dynamic: Dynamic-analysis section placeholder or correlated context.
        signals: Normalized finding list emitted by static rules.
        correlation: Correlation/scoring payload derived from findings/context.
        artifacts: Produced artifact references linked to this analysis.
    """

    schema_version: int
    profile: str
    summary: dict[str, Any]
    file_info: dict[str, Any]
    metadata: dict[str, Any]
    static: dict[str, Any]
    dynamic: dict[str, Any]
    signals: list[Signal]
    correlation: dict[str, Any]
    artifacts: list[dict[str, Any]]

    def to_dict(self) -> dict[str, Any]:
        """Serializes the analysis result into the persisted JSON contract.

        Returns:
            dict[str, Any]: JSON-ready mapping with normalized key names and
            an injected UTC ``generatedAt`` timestamp in ``metadata``.

        Side Effects:
            Reads current UTC time to generate ``metadata.generatedAt``.
        """
        return {
            "schemaVersion": self.schema_version,
            "profile": self.profile,
            "metadata": {
                "generatedAt": datetime.now(timezone.utc).isoformat(),
                **self.metadata,
            },
            "summary": self.summary,
            "fileInfo": self.file_info,
            "static": self.static,
            "dynamic": self.dynamic,
            "signals": [signal.to_dict() for signal in self.signals],
            "correlation": self.correlation,
            "artifacts": self.artifacts,
            "elfInfo": self.static.get("elf", {}),
        }
