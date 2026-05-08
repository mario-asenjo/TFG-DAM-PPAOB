"""Object-storage contracts for dynamic-worker binary and artifact payloads.

The protocol in this module abstracts remote object-storage access away from
application services. Implementations handle SDK specifics, retries, and
transport errors while preserving these call-level semantics.
"""

from __future__ import annotations

from typing import Protocol

from worker_dynamic.domain.models import AnalysisJob


class BinaryStorage(Protocol):
    """Storage boundary for input binaries and generated artifact bytes.

    Implementations are responsible for external object-storage interaction and
    should keep the interface focused on raw byte transfer primitives.
    """

    def download_binary(self, job: AnalysisJob) -> bytes:
        """Fetch the input binary payload for a claimed analysis job.

        Args:
            job: Claimed analysis metadata containing source storage location.

        Returns:
            Raw bytes of the input binary object.

        Raises:
            FileNotFoundError: If the referenced object cannot be found.
            PermissionError: If access to the object is denied.
            Exception: Adapter-specific transport or storage client errors.

        Side Effects:
            Performs an external network call and reads object data into memory.
        """

    def upload_bytes(self, bucket: str, object_key: str, payload: bytes, content_type: str) -> None:
        """Store an in-memory payload at a destination object-storage location.

        Args:
            bucket: Destination bucket/container name.
            object_key: Destination object key/path.
            payload: Raw bytes to upload.
            content_type: MIME type metadata associated with the object.

        Returns:
            None.

        Raises:
            PermissionError: If write access is denied.
            Exception: Adapter-specific transport or storage client errors.

        Side Effects:
            Performs an external network call that writes bytes to remote
            object storage.
        """
