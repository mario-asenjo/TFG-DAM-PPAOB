"""CLI bootstrap for the static analysis worker.

This module exposes the process entrypoint used by the static worker runtime.
The CLI does not define positional arguments or flags; configuration is loaded
entirely from environment variables through :meth:`WorkerSettings.from_env`.

Environment variables consumed (with defaults defined in settings):
    DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD,
    S3_ENDPOINT, S3_REGION, S3_ACCESS_KEY, S3_SECRET_KEY,
    WORKER_POLL_SECONDS, WORKER_PROFILE, WORKER_LOG_LEVEL,
    APP_CORRELATION_ENV_PROFILE, APP_CORRELATION_OBSERVED_RUNS,
    APP_DEPLOYMENT_EXPOSURE, APP_DEPLOYMENT_PRIVILEGE_LEVEL,
    APP_DEPLOYMENT_DATA_SENSITIVITY, APP_AUDIT_SYSTEM_USER_EMAIL.

Startup flow:
    1. Load immutable worker settings from environment.
    2. Configure process logging for ``static-worker``.
    3. Build repository, binary storage and analyzer adapters.
    4. Compose the application service and polling runner.
    5. Start the infinite polling loop.

Observable termination behavior:
    - Success path is long-running and does not return by design.
    - Configuration parsing or dependency failures propagate as exceptions and
      terminate the process with a non-zero exit status.
    - External termination signals (for example Ctrl+C) stop the process.
"""

from __future__ import annotations

import logging

from worker_static.adapters.db.postgres_repository import PostgresAnalysisRepository
from worker_static.adapters.storage.s3_storage import S3BinaryStorage
from worker_static.analysis.elf_analyzer import BaselineElfAnalyzer
from worker_static.app.runner import WorkerRunner
from worker_static.app.services import StaticAnalysisService
from worker_static.config.settings import WorkerSettings


def configure_logging(level: str) -> logging.Logger:
    """Configure and return the static worker logger instance.

    Args:
        level: Logging threshold accepted by ``logging.basicConfig``
            (for example ``"INFO"`` or ``"DEBUG"``).

    Returns:
        logging.Logger: Logger named ``static-worker`` used across the process.

    Side Effects:
        Configures global logging through ``logging.basicConfig`` for the
        current interpreter process.
    """
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [static-worker] %(message)s",
    )
    return logging.getLogger("static-worker")


def main() -> None:
    """Build dependencies and run the static worker polling loop.

    Args:
        None.

    Returns:
        None: The function is intended to block indefinitely while the worker
        is running.

    Raises:
        ValueError: If integer environment variables cannot be parsed while
            loading settings.
        Exception: Propagates adapter/service initialization or runtime errors
            from repository, storage, analyzer, or runner components.

    Side Effects:
        Reads worker configuration from environment variables, configures global
        logging, opens external connections (database and object storage), and
        starts continuous polling and analysis processing.
    """
    settings = WorkerSettings.from_env()
    logger = configure_logging(settings.worker_log_level)

    repository = PostgresAnalysisRepository(settings)
    storage = S3BinaryStorage(settings)
    analyzer = BaselineElfAnalyzer(
        settings.correlation_environment_profile,
        {
            "exposure": settings.deployment_exposure,
            "privilegeLevel": settings.deployment_privilege_level,
            "dataSensitivity": settings.deployment_data_sensitivity,
        },
        settings.correlation_observed_runs,
    )

    service = StaticAnalysisService(
        repository=repository,
        storage=storage,
        analyzer=analyzer,
        profile=settings.worker_profile,
        logger=logger,
    )

    runner = WorkerRunner(
        service=service,
        poll_seconds=settings.worker_poll_seconds,
        logger=logger,
    )
    runner.run_forever()


if __name__ == "__main__":
    main()
