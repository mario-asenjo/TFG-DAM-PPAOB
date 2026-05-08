"""S3-compatible storage adapter for dynamic worker artifacts and binaries.

The adapter encapsulates object-storage calls used by the dynamic worker to
download the input binary and upload generated artifact payloads.
"""

from __future__ import annotations

import boto3

from worker_dynamic.config.settings import WorkerSettings
from worker_dynamic.domain.models import AnalysisJob


class S3BinaryStorage:
    """Access binary payloads in S3-compatible object storage.

    This adapter centralizes `boto3` interactions so worker services depend on
    a stable storage contract instead of SDK details.
    """

    def __init__(self, settings: WorkerSettings) -> None:
        """Create a storage client from worker settings.

        Args:
            settings: Worker configuration containing S3 endpoint, region, and
                credentials.

        Side Effects:
            Creates a `boto3` S3 client configured for the target object
            storage service.
        """
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint,
            region_name=settings.s3_region,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
        )

    def download_binary(self, job: AnalysisJob) -> bytes:
        """Download the binary payload referenced by a claimed analysis job.

        Args:
            job: Analysis metadata containing source `bucket` and `object_key`.

        Returns:
            Raw binary bytes read from object storage.

        Raises:
            botocore.exceptions.BotoCoreError: If the SDK cannot complete the
                request.
            botocore.exceptions.ClientError: If the object does not exist or
                access fails.

        Side Effects:
            Performs an external network call to object storage and reads the
            response stream into memory.
        """
        response = self._client.get_object(Bucket=job.bucket, Key=job.object_key)
        return response["Body"].read()

    def upload_bytes(self, bucket: str, object_key: str, payload: bytes, content_type: str) -> None:
        """Upload an in-memory payload to object storage.

        Args:
            bucket: Destination bucket name.
            object_key: Destination object key/path.
            payload: Raw bytes payload to store.
            content_type: MIME type stored with the object metadata.

        Raises:
            botocore.exceptions.BotoCoreError: If the SDK cannot complete the
                request.
            botocore.exceptions.ClientError: If upload is rejected by the
                storage service.

        Side Effects:
            Performs an external network call that writes the payload to the
            configured object storage backend.
        """
        self._client.put_object(
            Bucket=bucket,
            Key=object_key,
            Body=payload,
            ContentType=content_type,
        )
