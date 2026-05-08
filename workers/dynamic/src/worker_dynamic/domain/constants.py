"""Status constants shared across dynamic-analysis job lifecycle.

These values represent persisted workflow states for analysis tasks. The
expected transition path is typically ``PENDING -> RUNNING -> DONE`` for a
successful execution, or ``PENDING/RUNNING -> FAILED`` when processing cannot
complete.

The constants are uppercase strings because they are exchanged with storage
layers and other worker components as stable serialized values.
"""

STATUS_PENDING = "PENDING"
"""Job is queued and available for claiming by a worker."""

STATUS_RUNNING = "RUNNING"
"""Job has been claimed and is currently being processed."""

STATUS_DONE = "DONE"
"""Job finished successfully and result payload was persisted."""

STATUS_FAILED = "FAILED"
"""Job ended with an unrecoverable processing error."""
