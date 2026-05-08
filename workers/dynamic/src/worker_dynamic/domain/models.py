"""Immutable domain models exchanged within the dynamic analysis worker.

The models in this module represent:
- claimed analysis jobs to execute in sandboxed runtime, and
- normalized result payloads persisted after analysis completes.

Contracts in this module are intentionally storage-agnostic, but field names
and shapes align with downstream JSON produced by worker services.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID


@dataclass(frozen=True)
class AnalysisJob:
    """Represents one claimed dynamic analysis job.

    Instances are immutable snapshots of repository data at claim time.

    Attributes:
        analysis_id: Unique identifier of the analysis request instance.
        binary_id: Identifier of the binary sample under analysis.
        requested_by: Optional identifier of the requesting user.
        profile: Analysis profile selected for this execution.
        original_name: Original filename reported at ingestion time.
        sha256: Canonical SHA-256 digest of the analyzed binary.
        size_bytes: Binary size in bytes used for metadata consistency.
        bucket: Object storage bucket containing the binary object.
        object_key: Object storage key used to download the binary payload.

    Invariants:
        - ``analysis_id`` and ``binary_id`` are stable UUIDs.
        - ``sha256`` corresponds to the binary bytes referenced by
          ``bucket``/``object_key``.
        - ``size_bytes`` is non-negative and reflects persisted ingest metadata.
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
class DynamicResult:
    """Result payload persisted into ``analysis_results``.

    This model captures structured telemetry generated during dynamic analysis
    and correlation with static indicators. Field names use snake_case in
    Python and are converted to API/storage JSON keys by :meth:`to_dict`.

    Attributes:
        schema_version: Version of the result schema contract.
        profile: Profile used to run the dynamic analysis.
        metadata: Additional run metadata merged into serialized output.
        file_info: Normalized file metadata (hashes, name, size, type).
        summary: High-level scoring and classification information.
        static: Selected static-analysis excerpt used for correlation.
        dynamic: Dynamic-analysis telemetry and derived aggregates.
        signals: Ordered signal list consumed by scoring/reporting layers.
        correlation: Mapping of relationships between static/dynamic evidence.
        artifacts: Generated artifact descriptors (paths, MIME, roles, etc.).
        trace_ndjson: Optional raw trace representation for archival purposes.

    Consistency rules:
        - ``schema_version`` identifies payload interpretation semantics.
        - ``profile`` should match the profile from the associated
          :class:`AnalysisJob`.
        - ``dynamic`` may omit ``runtime``, ``policy``, or ``topSyscalls``;
          :meth:`to_dict` provides normalized fallback values.
    """

    schema_version: int
    profile: str
    metadata: dict
    file_info: dict
    summary: dict
    static: dict
    dynamic: dict
    signals: list[dict]
    correlation: dict
    artifacts: list[dict]
    trace_ndjson: str | None = None

    def to_dict(self) -> dict:
        """Serialize the result payload into JSON-ready dictionary form.

        The serializer emits camelCase keys expected by downstream persistence
        and API layers, injects ``metadata.generatedAt`` using current UTC time,
        and normalizes frequently-consumed dynamic substructures.

        Returns:
            dict: Serialized payload ready for JSON encoding and persistence.

        Side Effects:
            Reads the current system time to populate
            ``metadata.generatedAt``.
        """
        runtime = self.dynamic.get("runtime", {})
        policy = self.dynamic.get("policy", {})
        top_syscalls = self.dynamic.get("topSyscalls", [])
        return {
            "schemaVersion": self.schema_version,
            "profile": self.profile,
            "metadata": {
                "generatedAt": datetime.now(timezone.utc).isoformat(),
                **self.metadata,
            },
            "fileInfo": self.file_info,
            "summary": self.summary,
            "static": self.static,
            "dynamic": self.dynamic,
            "signals": self.signals,
            "correlation": self.correlation,
            "artifacts": self.artifacts,
            "runtime": runtime,
            "policy": policy,
            "topSyscalls": top_syscalls,
        }
