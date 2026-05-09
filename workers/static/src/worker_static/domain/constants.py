"""Canonical domain constants for static-analysis lifecycle and findings.

The status constants model the expected analysis job state machine as observed
by adapters/services:

- ``PENDING`` -> ``RUNNING`` -> ``DONE`` for successful completion.
- ``PENDING`` -> ``RUNNING`` -> ``FAILED`` for terminal failures.

Severity constants are normalized labels used in emitted static-analysis
signals to support consistent downstream aggregation and reporting.
"""

STATUS_PENDING = "PENDING"
STATUS_RUNNING = "RUNNING"
STATUS_DONE = "DONE"
STATUS_FAILED = "FAILED"

SEVERITY_HIGH = "HIGH"
SEVERITY_MEDIUM = "MEDIUM"
SEVERITY_LOW = "LOW"
