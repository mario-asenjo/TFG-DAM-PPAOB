"""S3-compatible storage adapter for static worker binaries.

This module provides a concrete storage adapter that resolves binary content
from object storage metadata embedded in claimed analysis jobs.
"""

from __future__ import annotations

import boto3

from worker_static.config.settings import WorkerSettings
from worker_static.domain.models import AnalysisJob


class S3BinaryStorage:
    """Download binary payloads from an S3-compatible object store.

    The adapter is intentionally thin: it delegates credential handling,
    endpoint transport, and response streaming to ``boto3``.
    """

    def __init__(self, settings: WorkerSettings) -> None:
        """Build an S3 client using worker configuration.

        Args:
            settings: Static worker settings containing endpoint, region, and
                credential material for the S3-compatible backend.

        Side Effects:
            Initializes a ``boto3`` client object bound to the configured
            endpoint and credentials.
        """
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint,
            region_name=settings.s3_region,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
        )

    def download_binary(self, job: AnalysisJob) -> bytes:
        """Download binary bytes referenced by a claimed analysis job.

        Args:
            job: Claimed job containing ``bucket`` and ``object_key`` values
                previously loaded from persistence.

        Returns:
            Raw object bytes exactly as stored in object storage.

        Raises:
            botocore.exceptions.BotoCoreError: If the client cannot complete
                the request because of transport/session issues.
            botocore.exceptions.ClientError: If the object cannot be fetched
                (for example missing key, denied access, or invalid bucket).
            KeyError: If the response does not include the expected ``Body``
                stream key.

        Side Effects:
            Performs a remote object-storage read over the network.
        """
        response = self._client.get_object(Bucket=job.bucket, Key=job.object_key)
        return response["Body"].read()
