"""Object storage contracts used by static-analysis orchestration.

This module defines the storage boundary consumed by application services.
Implementations are responsible for remote I/O details, authentication, and
provider-specific exceptions, while the service layer depends on this stable
contract.
"""

from __future__ import annotations

from typing import Protocol

from worker_static.domain.models import AnalysisJob


class BinaryStorage(Protocol):
    """Read-only contract for resolving binary payload bytes.

    Responsibilities:
    - Fetch object content identified by storage metadata inside ``AnalysisJob``.
    - Return payload bytes exactly as stored, without analysis-specific changes.

    Non-responsibilities:
    - Job-state transitions.
    - Result persistence.
    - Retry/backoff policy orchestration.
    """

    def download_binary(self, job: AnalysisJob) -> bytes:
        """Download payload bytes for one claimed analysis job.

        Args:
            job: Claimed analysis job carrying the object-location metadata
                required by the storage backend (for example bucket and key).

        Returns:
            bytes: Raw binary content associated with ``job``.

        Raises:
            Exception: Backend-specific storage/transport/authentication errors
                when the object cannot be read.
            KeyError: If an implementation expects required location fields and
                they are missing from the job metadata.

        Side Effects:
            Performs external object-storage reads over the network or other
            delegated storage transport.
        """
