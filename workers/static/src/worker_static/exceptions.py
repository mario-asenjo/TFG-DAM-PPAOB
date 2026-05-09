"""Exception contracts shared across the static worker package.

The static worker raises domain-specific exceptions so callers can distinguish
misconfiguration failures from transient runtime issues. This module defines
the stable exception type consumed by bootstrap code (CLI/runner) and tests.

Propagation boundary:
- this module only declares exception classes;
- exceptions are instantiated/raised by configuration or orchestration layers;
- top-level entry points may convert these exceptions into process exit codes
  or structured error logs.
"""


class WorkerConfigurationError(RuntimeError):
    """Configuration contract violation for static worker startup.

    Raised when required configuration inputs are missing or invalid during
    worker bootstrap (for example, required environment variables parsed by the
    settings layer).

    Observable contract for consumers:
    - this error indicates a non-recoverable local setup problem rather than a
      repository/storage transient failure;
    - callers should treat it as a startup-time failure and stop processing
      until configuration is corrected.
    """
