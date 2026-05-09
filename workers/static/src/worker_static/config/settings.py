"""Runtime settings for the static worker.

Environment variables supported by this module:

- ``DB_HOST`` (default: ``"postgres"``)
- ``DB_PORT`` (default: ``"5432"``, coerced to ``int``)
- ``DB_NAME`` (default: ``"ppaob"``)
- ``DB_USER`` (default: ``"ppaob"``)
- ``DB_PASSWORD`` (default: ``"ppaob_dev_password"``)
- ``S3_ENDPOINT`` (default: ``"http://minio:9000"``)
- ``S3_REGION`` (default: ``"us-east-1"``)
- ``S3_ACCESS_KEY`` (default: ``"minioadmin"``)
- ``S3_SECRET_KEY`` (default: ``"minioadmin_dev_password"``)
- ``WORKER_POLL_SECONDS`` (default: ``"3"``, coerced to ``int``)
- ``WORKER_PROFILE`` (default: ``"STATIC_BASELINE"``)
- ``APP_CORRELATION_ENV_PROFILE`` (default: ``"LINUX_SERVER"``)
- ``APP_CORRELATION_OBSERVED_RUNS`` (default: ``"1"``, coerced to ``int``)
- ``APP_DEPLOYMENT_EXPOSURE`` (default: ``"INTERNAL"``)
- ``APP_DEPLOYMENT_PRIVILEGE_LEVEL`` (default: ``"USER"``)
- ``APP_DEPLOYMENT_DATA_SENSITIVITY`` (default: ``"MEDIUM"``)
- ``WORKER_LOG_LEVEL`` (default: ``"INFO"``)
- ``APP_AUDIT_SYSTEM_USER_EMAIL`` (default: ``"system@ppaob.local"``)
- ``APP_RUNTIME_MODE`` (default: ``"dev"``)

Bootstrap validation/failures:

- Numeric values are validated through ``int(...)`` coercion.
- Worker bootstrap fails with ``ValueError`` if ``DB_PORT``,
  ``WORKER_POLL_SECONDS`` or ``APP_CORRELATION_OBSERVED_RUNS`` are not valid
  base-10 integers.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class WorkerSettings:
    """Immutable static-worker configuration loaded from process environment.

    Attributes map one-to-one to supported environment variables and represent
    the effective values used by the worker after applying defaults and type
    coercions.
    """

    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    s3_endpoint: str
    s3_region: str
    s3_access_key: str
    s3_secret_key: str
    worker_poll_seconds: int
    worker_profile: str
    correlation_environment_profile: str
    correlation_observed_runs: int
    deployment_exposure: str
    deployment_privilege_level: str
    deployment_data_sensitivity: str
    worker_log_level: str
    audit_system_user_email: str
    runtime_mode: str

    @classmethod
    def from_env(cls) -> "WorkerSettings":
        """Build runtime settings from environment variables.

        Returns:
            WorkerSettings: Frozen settings instance with effective defaults,
            string values as-is, and numeric fields coerced to ``int``.

        Raises:
            ValueError: If ``DB_PORT``, ``WORKER_POLL_SECONDS`` or
                ``APP_CORRELATION_OBSERVED_RUNS`` cannot be parsed as integers.

        Side Effects:
            Reads process environment variables via ``os.getenv`` during worker
            bootstrap.
        """
        settings = cls(
            db_host=os.getenv("DB_HOST", "postgres"),
            db_port=int(os.getenv("DB_PORT", "5432")),
            db_name=os.getenv("DB_NAME", "ppaob"),
            db_user=os.getenv("DB_USER", "ppaob"),
            db_password=os.getenv("DB_PASSWORD", "ppaob_dev_password"),
            s3_endpoint=os.getenv("S3_ENDPOINT", "http://minio:9000"),
            s3_region=os.getenv("S3_REGION", "us-east-1"),
            s3_access_key=os.getenv("S3_ACCESS_KEY", "minioadmin"),
            s3_secret_key=os.getenv("S3_SECRET_KEY", "minioadmin_dev_password"),
            worker_poll_seconds=int(os.getenv("WORKER_POLL_SECONDS", "3")),
            worker_profile=os.getenv("WORKER_PROFILE", "STATIC_BASELINE"),
            correlation_environment_profile=os.getenv(
                "APP_CORRELATION_ENV_PROFILE",
                "LINUX_SERVER",
            ),
            correlation_observed_runs=int(os.getenv("APP_CORRELATION_OBSERVED_RUNS", "1")),
            deployment_exposure=os.getenv("APP_DEPLOYMENT_EXPOSURE", "INTERNAL"),
            deployment_privilege_level=os.getenv("APP_DEPLOYMENT_PRIVILEGE_LEVEL", "USER"),
            deployment_data_sensitivity=os.getenv("APP_DEPLOYMENT_DATA_SENSITIVITY", "MEDIUM"),
            worker_log_level=os.getenv("WORKER_LOG_LEVEL", "INFO"),
            audit_system_user_email=os.getenv(
                "APP_AUDIT_SYSTEM_USER_EMAIL",
                "system@ppaob.local",
            ),
            runtime_mode=os.getenv("APP_RUNTIME_MODE", "dev"),
        )
        _validate_runtime_settings(settings)
        return settings


def _validate_runtime_settings(settings: WorkerSettings) -> None:
    mode = settings.runtime_mode.strip().lower()
    if mode not in {"prod", "prod-like"}:
        return

    if settings.db_password == "ppaob_dev_password":
        raise ValueError("DB_PASSWORD cannot use the development default in prod-like/prod mode")
    if settings.s3_secret_key == "minioadmin_dev_password":
        raise ValueError("S3_SECRET_KEY cannot use the development default in prod-like/prod mode")
