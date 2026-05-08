"""Signal extraction rules for baseline static analysis.

The rules in this module map string/symbol presence to normalized `Signal`
objects consumed by correlation/scoring. Matching is substring-based and does
not guarantee that a function is reachable at runtime.
"""

from __future__ import annotations

from worker_static.domain.constants import SEVERITY_HIGH, SEVERITY_MEDIUM
from worker_static.domain.models import Signal

UNSAFE_FUNCTIONS: dict[str, tuple[str, str]] = {
    "strcpy": ("unsafe_copy_function", SEVERITY_HIGH),
    "strcat": ("unsafe_copy_function", SEVERITY_MEDIUM),
    "gets": ("unsafe_input_function", SEVERITY_HIGH),
    "sprintf": ("unsafe_copy_function", SEVERITY_MEDIUM),
}

BEHAVIOR_FUNCTIONS: dict[str, tuple[str, str, str]] = {
    "system": (
        "command_execution_function",
        SEVERITY_HIGH,
        "Command execution API reference detected",
    ),
    "popen": (
        "command_execution_function",
        SEVERITY_HIGH,
        "Process pipe execution API reference detected",
    ),
    "execve": (
        "command_execution_function",
        SEVERITY_HIGH,
        "Direct exec API reference detected",
    ),
    "execvp": (
        "command_execution_function",
        SEVERITY_HIGH,
        "Path-based exec API reference detected",
    ),
    "fork": (
        "process_control_function",
        SEVERITY_MEDIUM,
        "Process forking API reference detected",
    ),
    "setuid": (
        "privilege_manipulation_function",
        SEVERITY_HIGH,
        "Privilege change API reference detected",
    ),
    "setgid": (
        "privilege_manipulation_function",
        SEVERITY_HIGH,
        "Group privilege API reference detected",
    ),
    "socket": (
        "network_function",
        SEVERITY_MEDIUM,
        "Network socket API reference detected",
    ),
    "connect": (
        "network_function",
        SEVERITY_MEDIUM,
        "Outgoing network API reference detected",
    ),
    "listen": (
        "network_function",
        SEVERITY_MEDIUM,
        "Listening network API reference detected",
    ),
}

SENSITIVE_LITERALS: dict[str, tuple[str, str, str]] = {
    "/etc/passwd": (
        "sensitive_path_literal",
        SEVERITY_HIGH,
        "Sensitive account database path literal detected",
    ),
    "/etc/shadow": (
        "sensitive_path_literal",
        SEVERITY_HIGH,
        "Sensitive password hash path literal detected",
    ),
    "/root/.ssh": (
        "sensitive_path_literal",
        SEVERITY_HIGH,
        "Root SSH material path literal detected",
    ),
    "/var/run/docker.sock": (
        "sensitive_path_literal",
        SEVERITY_HIGH,
        "Container control socket literal detected",
    ),
    "/bin/sh": (
        "shell_literal",
        SEVERITY_HIGH,
        "Shell invocation literal detected",
    ),
    "bash -c": (
        "shell_literal",
        SEVERITY_HIGH,
        "Command shell expansion literal detected",
    ),
}


def detect_unsafe_function_signals(symbol_blob: str) -> list[Signal]:
    """Detect unsafe runtime API references from extracted text.

    Args:
        symbol_blob: Raw concatenated symbol/string content extracted from the
            binary payload.

    Returns:
        List of normalized `Signal` entries for configured unsafe C runtime
        functions that appear in the input text.

    Side Effects:
        None.

    Notes:
        Detection is lexical and case-sensitive for this rule set. The output
        indicates indicator presence, not confirmed vulnerable execution.
    """
    signals: list[Signal] = []
    for function_name, (kind, severity) in UNSAFE_FUNCTIONS.items():
        if function_name not in symbol_blob:
            continue
        signals.append(
            Signal(
                id=f"UNSAFE_FUNCTION_{function_name.upper()}",
                kind=kind,
                severity=severity,
                title=f"Detected reference to {function_name}",
                evidence={"symbol": function_name},
            )
        )
    return signals


def detect_behavior_signals(symbol_blob: str) -> list[Signal]:
    """Detect behavior-oriented indicators from symbol/string evidence.

    Args:
        symbol_blob: Raw concatenated symbol/string content extracted from the
            binary payload.

    Returns:
        List of normalized `Signal` entries associated with command execution,
        process control, privilege manipulation, network behavior, and
        sensitive literal references.

    Side Effects:
        None.

    Notes:
        Input is lowercased before matching, and detection is substring-based.
        Matches represent static indicators and should be interpreted together
        with correlation context.
    """
    lowered = symbol_blob.lower()
    signals: list[Signal] = []
    for token, (kind, severity, title) in BEHAVIOR_FUNCTIONS.items():
        if token not in lowered:
            continue
        signals.append(
            Signal(
                id=f"BEHAVIOR_{token.upper()}",
                kind=kind,
                severity=severity,
                title=title,
                evidence={"token": token},
            )
        )

    for literal, (kind, severity, title) in SENSITIVE_LITERALS.items():
        if literal not in lowered:
            continue
        clean = (
            literal.upper()
            .replace("/", "_")
            .replace(".", "_")
            .replace(" ", "_")
            .strip("_")
        )
        signals.append(
            Signal(
                id=f"LITERAL_{clean}",
                kind=kind,
                severity=severity,
                title=title,
                evidence={"literal": literal},
            )
        )

    return signals
