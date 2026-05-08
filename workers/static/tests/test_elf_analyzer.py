"""Contract tests for baseline ELF static analysis outputs.

These tests validate parser and correlation behavior exposed to the worker
result contract, using fixture binaries that represent benign and risky
pre-exploitation patterns.
"""

from pathlib import Path
from uuid import uuid4

from worker_static.analysis.elf_analyzer import BaselineElfAnalyzer
from worker_static.domain.models import AnalysisJob


def _load_example_binary(name: str) -> bytes:
    fixture = Path(__file__).resolve().parents[3] / "example_programs" / name
    return fixture.read_bytes()


def _patch_elf_type_to_exec(payload: bytes) -> bytes:
    patched = bytearray(payload)
    if patched[5] == 1:
        patched[16] = 0x02
        patched[17] = 0x00
    elif patched[5] == 2:
        patched[16] = 0x00
        patched[17] = 0x02
    return bytes(patched)


def test_detects_strcpy_signal() -> None:
    """Validate unsafe libc usage is surfaced as a high-risk signal.

    The fixture includes strcpy evidence; assertions cover signal presence,
    minimum calibrated risk, and baseline ELF/correlation envelope fields.

    Args:
        None.

    Returns:
        None.
    """
    analyzer = BaselineElfAnalyzer()
    job = AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="STATIC_BASELINE",
        original_name="test.elf",
        sha256="a" * 64,
        size_bytes=32,
        bucket="bucket",
        object_key="key",
    )
    payload = _load_example_binary("unsafe_strcpy")

    result = analyzer.analyze(job, payload)

    signal_ids = [signal.id for signal in result.signals]
    assert "UNSAFE_FUNCTION_STRCPY" in signal_ids
    assert result.summary["riskLevel"] in {"MEDIUM", "HIGH"}
    assert result.summary["riskScore"] >= 35
    assert result.correlation["environmentProfile"] == "LINUX_SERVER"
    assert result.static["elf"]["isElf"] is True


def test_correlates_command_and_network_procedure_paths() -> None:
    """Validate multi-signal binaries map to chained procedure tracks.

    The fixture combines command and network artifacts; assertions cover
    required procedures and high-risk scoring for merged behavior paths.

    Args:
        None.

    Returns:
        None.
    """
    analyzer = BaselineElfAnalyzer()
    job = AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="STATIC_BASELINE",
        original_name="complex.elf",
        sha256="b" * 64,
        size_bytes=64,
        bucket="bucket",
        object_key="key",
    )
    payload = _load_example_binary("combined_remote_exec_pattern")

    result = analyzer.analyze(job, payload)

    procedure_ids = [entry["procedureId"] for entry in result.correlation["procedures"]]
    assert "PROC_COMMAND_EXEC" in procedure_ids
    assert "PROC_NETWORK_IO" in procedure_ids
    assert (
        "PROC_PRIVILEGED_RESOURCE_FLOW" in procedure_ids
        or "PROC_MEMORY_UNSAFE_HANDLING" in procedure_ids
    )
    assert result.correlation["riskScore"] >= 70


def test_extracts_elf_sections_segments_dependencies_and_mitigations() -> None:
    """Validate structured ELF metadata extraction for valid payloads.

    Assertions cover identity fields plus non-empty sections/segments and
    mitigation/dependency collections consumed by reporting.

    Args:
        None.

    Returns:
        None.
    """
    analyzer = BaselineElfAnalyzer()
    job = AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="STATIC_BASELINE",
        original_name="command_exec_system",
        sha256="c" * 64,
        size_bytes=64,
        bucket="bucket",
        object_key="key",
    )

    result = analyzer.analyze(job, _load_example_binary("command_exec_system"))

    assert result.static["elf"]["isElf"] is True
    assert result.static["elf"]["architecture"] != "UNKNOWN"
    assert isinstance(result.static["elf"]["entrypoint"], int)
    assert result.static["elf"]["elfType"].startswith("ET_")
    assert len(result.static["sections"]) > 0
    assert len(result.static["segments"]) > 0
    assert isinstance(result.static["dependencies"], list)
    assert "pie" in result.static["mitigations"]
    assert "nx" in result.static["mitigations"]
    assert "relro" in result.static["mitigations"]
    assert "canary" in result.static["mitigations"]


def test_non_elf_payload_has_empty_structured_elf_details() -> None:
    """Validate non-ELF payloads return safe empty structured fields.

    Assertions confirm graceful fallback (`isElf=False`) with empty metadata
    collections instead of parser failures.

    Args:
        None.

    Returns:
        None.
    """
    analyzer = BaselineElfAnalyzer()
    job = AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="STATIC_BASELINE",
        original_name="not-elf.bin",
        sha256="d" * 64,
        size_bytes=12,
        bucket="bucket",
        object_key="key",
    )

    result = analyzer.analyze(job, b"not an elf")

    assert result.static["elf"]["isElf"] is False
    assert result.static["sections"] == []
    assert result.static["segments"] == []
    assert result.static["dependencies"] == []


def test_detects_pie_and_non_pie_from_elf_type() -> None:
    """Validate PIE mitigation flag follows ELF type semantics.

    A patched ET_EXEC payload is compared to original ET_DYN output to ensure
    PIE is enabled only for dynamic-position-independent ELF binaries.

    Args:
        None.

    Returns:
        None.
    """
    analyzer = BaselineElfAnalyzer()
    job = AnalysisJob(
        analysis_id=uuid4(),
        binary_id=uuid4(),
        requested_by=uuid4(),
        profile="STATIC_BASELINE",
        original_name="unsafe_strcpy",
        sha256="e" * 64,
        size_bytes=64,
        bucket="bucket",
        object_key="key",
    )
    payload = _load_example_binary("unsafe_strcpy")

    pie_result = analyzer.analyze(job, payload)
    non_pie_result = analyzer.analyze(job, _patch_elf_type_to_exec(payload))

    assert pie_result.static["mitigations"]["pie"]["enabled"] == (
        pie_result.static["elf"]["elfType"] == "ET_DYN"
    )
    assert non_pie_result.static["elf"]["elfType"] == "ET_EXEC"
    assert non_pie_result.static["mitigations"]["pie"]["enabled"] is False
