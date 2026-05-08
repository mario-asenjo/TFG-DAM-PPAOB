"""Baseline ELF analyzer used by static worker.

The analyzer composes three stages:
1. Structural ELF parsing (header, sections, segments, dependencies,
   mitigations) when payload has a valid ELF signature.
2. Signal extraction from printable ASCII strings using static rule sets.
3. Profile-based correlation/scoring to prioritize findings by environment.

Results provide pre-exploitation triage signals. They do not prove runtime
reachability, successful exploitation, or post-compromise behavior.
"""

from __future__ import annotations

import io
import re

from elftools.elf.elffile import ELFFile

from worker_static.analysis.correlation import build_correlation
from worker_static.analysis.correlation import resolve_risk_level_from_score
from worker_static.analysis.signal_rules import detect_behavior_signals
from worker_static.analysis.signal_rules import detect_unsafe_function_signals
from worker_static.domain.constants import (
    SEVERITY_HIGH,
    SEVERITY_LOW,
    SEVERITY_MEDIUM,
)
from worker_static.domain.models import AnalysisJob, AnalysisResult, Signal


class BaselineElfAnalyzer:
    """Perform baseline static analysis over ELF binary bytes.

    The analyzer returns an `AnalysisResult` with normalized static evidence,
    extracted signals, and correlation output suitable for downstream
    prioritization.
    """

    def __init__(
        self,
        correlation_environment_profile: str = "LINUX_SERVER",
        deployment_context: dict | None = None,
        observed_runs: int = 1,
    ) -> None:
        """Initialize analyzer context used by correlation/scoring.

        Args:
            correlation_environment_profile: Correlation profile key (for
                example `LINUX_SERVER` or `CONTAINER_SERVICE`).
            deployment_context: Optional assumptions about exposure,
                privilege level, and data sensitivity.
            observed_runs: Number of confirmations for the same sample;
                values below 1 are normalized to 1.

        Raises:
            ValueError: If `observed_runs` cannot be converted to integer.

        Side Effects:
            None.
        """
        self._correlation_environment_profile = correlation_environment_profile
        self._deployment_context = deployment_context or {}
        self._observed_runs = max(1, int(observed_runs))

    def analyze(self, job: AnalysisJob, payload: bytes) -> AnalysisResult:
        """Analyze one binary payload and return a structured baseline result.

        Args:
            job: Analysis job metadata used to populate output identifiers and
                file attributes.
            payload: Raw binary bytes to inspect.

        Returns:
            `AnalysisResult` containing:
            - static ELF attributes when parseable;
            - normalized indicator `signals` from string-based detection;
            - correlation/scoring output driven by profile and context.

        Side Effects:
            Parses payload in-memory and triggers correlation profile loading.

        Notes:
            Non-ELF payloads still produce a valid result with default static
            fields and heuristic signal/correlation output. Signal extraction is
            based on printable ASCII substrings and can include false positives.
        """
        is_elf = len(payload) >= 4 and payload[0:4] == b"\x7fELF"
        elf_details = self._parse_elf_details(payload, is_elf)

        string_content = "\n".join(self._extract_ascii_strings(payload))
        signals = (
            detect_unsafe_function_signals(string_content)
            + detect_behavior_signals(string_content)
        )
        correlation = build_correlation(
            signals,
            self._correlation_environment_profile,
            self._deployment_context,
            self._observed_runs,
        )
        risk_level = self._resolve_risk_level(
            signals,
            int(correlation.get("riskScore", 0)),
            self._correlation_environment_profile,
        )

        return AnalysisResult(
            schema_version=1,
            profile=job.profile,
            metadata={
                "analysisId": str(job.analysis_id),
                "binaryId": str(job.binary_id),
                "requestedProfile": job.profile,
                "producer": "worker-static",
            },
            summary={
                "riskLevel": risk_level,
                "findingsCount": len(signals),
                "riskScore": correlation.get("riskScore", 0),
            },
            file_info={
                "binaryId": str(job.binary_id),
                "originalName": job.original_name,
                "sha256": job.sha256,
                "sizeBytes": job.size_bytes,
            },
            static={
                "elf": elf_details["elf"],
                "sections": elf_details["sections"],
                "segments": elf_details["segments"],
                "dependencies": elf_details["dependencies"],
                "mitigations": elf_details["mitigations"],
            },
            dynamic={},
            signals=signals,
            correlation=correlation,
            artifacts=[],
        )

    @staticmethod
    def _extract_ascii_strings(payload: bytes) -> list[str]:
        """Extract simple printable ASCII strings for baseline heuristics."""
        matches = re.findall(rb"[\x20-\x7E]{4,}", payload)
        return [match.decode("ascii", errors="ignore") for match in matches]

    @staticmethod
    def _resolve_elf_class(payload: bytes, is_elf: bool) -> str:
        """Resolve ELF class marker from header bytes."""
        if not is_elf or len(payload) < 5:
            return "UNKNOWN"
        return {1: "ELF32", 2: "ELF64"}.get(payload[4], "UNKNOWN")

    @staticmethod
    def _resolve_endianness(payload: bytes, is_elf: bool) -> str:
        """Resolve ELF endianness marker from header bytes."""
        if not is_elf or len(payload) < 6:
            return "UNKNOWN"
        return {1: "LITTLE_ENDIAN", 2: "BIG_ENDIAN"}.get(payload[5], "UNKNOWN")

    def _parse_elf_details(self, payload: bytes, is_elf: bool) -> dict:
        """Build static ELF fields with best-effort parser behavior."""
        default = {
            "elf": {
                "isElf": is_elf,
                "class": self._resolve_elf_class(payload, is_elf),
                "endianness": self._resolve_endianness(payload, is_elf),
                "architecture": "UNKNOWN",
                "entrypoint": None,
                "elfType": "UNKNOWN",
            },
            "sections": [],
            "segments": [],
            "dependencies": [],
            "mitigations": {
                "nx": {"enabled": False, "status": "UNKNOWN"},
                "pie": {"enabled": False, "status": "UNKNOWN"},
                "relro": {"enabled": False, "status": "UNKNOWN"},
                "canary": {"enabled": False, "status": "UNKNOWN"},
            },
        }
        if not is_elf:
            return default

        try:
            elf_file = ELFFile(io.BytesIO(payload))
        except Exception:
            return default

        elf_type = str(elf_file.header.get("e_type", "UNKNOWN"))
        architecture = str(elf_file.header.get("e_machine", "UNKNOWN"))
        entrypoint = int(elf_file.header.get("e_entry", 0))

        sections = [
            {
                "name": section.name,
                "type": str(section.header.get("sh_type", "")),
                "addr": int(section.header.get("sh_addr", 0)),
                "size": int(section.header.get("sh_size", 0)),
                "flags": int(section.header.get("sh_flags", 0)),
            }
            for section in elf_file.iter_sections()
        ]
        segments = [
            {
                "type": str(segment.header.get("p_type", "")),
                "vaddr": int(segment.header.get("p_vaddr", 0)),
                "memsz": int(segment.header.get("p_memsz", 0)),
                "filesz": int(segment.header.get("p_filesz", 0)),
                "flags": int(segment.header.get("p_flags", 0)),
            }
            for segment in elf_file.iter_segments()
        ]
        dependencies = self._extract_dt_needed(elf_file)
        mitigations = self._extract_mitigations(elf_file, elf_type)

        return {
            "elf": {
                "isElf": True,
                "class": self._resolve_elf_class(payload, True),
                "endianness": self._resolve_endianness(payload, True),
                "architecture": architecture,
                "entrypoint": entrypoint,
                "elfType": elf_type,
            },
            "sections": sections,
            "segments": segments,
            "dependencies": dependencies,
            "mitigations": mitigations,
        }

    @staticmethod
    def _extract_dt_needed(elf_file: ELFFile) -> list[str]:
        """Extract shared library dependencies from .dynamic section."""
        dependencies: list[str] = []
        dynamic = elf_file.get_section_by_name(".dynamic")
        if dynamic is None:
            return dependencies
        for tag in dynamic.iter_tags():
            if str(tag.entry.d_tag) == "DT_NEEDED":
                dependencies.append(str(tag.needed))
        return dependencies

    @staticmethod
    def _extract_mitigations(elf_file: ELFFile, elf_type: str) -> dict:
        """Derive NX/PIE/RELRO/canary mitigation status."""
        nx_enabled = False
        nx_status = "UNKNOWN"
        stack_segment = next(
            (
                segment
                for segment in elf_file.iter_segments()
                if str(segment.header.get("p_type")) == "PT_GNU_STACK"
            ),
            None,
        )
        if stack_segment is not None:
            flags = int(stack_segment.header.get("p_flags", 0))
            nx_enabled = (flags & 0x1) == 0
            nx_status = "ENABLED" if nx_enabled else "DISABLED"

        pie_enabled = elf_type == "ET_DYN"
        pie_status = "ENABLED" if pie_enabled else "DISABLED"

        has_relro_segment = any(
            str(segment.header.get("p_type")) == "PT_GNU_RELRO"
            for segment in elf_file.iter_segments()
        )
        bind_now = False
        dynamic = elf_file.get_section_by_name(".dynamic")
        if dynamic is not None:
            for tag in dynamic.iter_tags():
                d_tag = str(tag.entry.d_tag)
                if d_tag == "DT_BIND_NOW":
                    bind_now = True
                    break
                if d_tag == "DT_FLAGS":
                    bind_now = bool(int(tag.entry.d_val) & 0x8)
                if d_tag == "DT_FLAGS_1":
                    bind_now = bind_now or bool(int(tag.entry.d_val) & 0x1)
        relro_status = "NONE"
        if has_relro_segment and bind_now:
            relro_status = "FULL"
        elif has_relro_segment:
            relro_status = "PARTIAL"

        canary_enabled = BaselineElfAnalyzer._has_symbol(elf_file, "__stack_chk_fail")
        canary_status = "ENABLED" if canary_enabled else "NOT_DETECTED"

        return {
            "nx": {"enabled": nx_enabled, "status": nx_status},
            "pie": {"enabled": pie_enabled, "status": pie_status},
            "relro": {"enabled": has_relro_segment, "status": relro_status},
            "canary": {"enabled": canary_enabled, "status": canary_status},
        }

    @staticmethod
    def _has_symbol(elf_file: ELFFile, symbol_name: str) -> bool:
        """Check whether a symbol exists in symtab or dynsym."""
        for section_name in (".dynsym", ".symtab"):
            section = elf_file.get_section_by_name(section_name)
            if section is None:
                continue
            for symbol in section.iter_symbols():
                if symbol.name == symbol_name:
                    return True
        return False

    @staticmethod
    def _resolve_risk_level(
        signals: list[Signal],
        risk_score: int,
        environment_profile: str,
    ) -> str:
        """Resolve aggregate risk level from signal severities."""
        profile_risk = resolve_risk_level_from_score(risk_score, environment_profile)
        if profile_risk in {SEVERITY_HIGH, SEVERITY_MEDIUM}:
            return profile_risk
        severities = {signal.severity for signal in signals}
        if SEVERITY_HIGH in severities:
            return SEVERITY_HIGH
        if SEVERITY_MEDIUM in severities:
            return SEVERITY_MEDIUM
        return SEVERITY_LOW
