"""Command-line bootstrap for the dynamic worker process.

This module is executed as ``python -m worker_dynamic.cli.main`` and does not
accept positional arguments or options. Runtime configuration is read from
environment variables through :meth:`worker_dynamic.config.settings.WorkerSettings.from_env`.

Environment variables consumed at startup (with defaults defined in settings)
include:

- Database connectivity: ``DB_HOST``, ``DB_PORT``, ``DB_NAME``, ``DB_USER``,
  ``DB_PASSWORD``.
- Object storage connectivity: ``S3_ENDPOINT``, ``S3_REGION``, ``S3_ACCESS_KEY``,
  ``S3_SECRET_KEY``.
- Worker loop behavior: ``WORKER_POLL_SECONDS``, ``WORKER_PROFILE``,
  ``WORKER_LOG_LEVEL``.
- Correlation and deployment context: ``APP_CORRELATION_ENV_PROFILE``,
  ``APP_CORRELATION_OBSERVED_RUNS``, ``APP_DEPLOYMENT_EXPOSURE``,
  ``APP_DEPLOYMENT_PRIVILEGE_LEVEL``, ``APP_DEPLOYMENT_DATA_SENSITIVITY``,
  ``APP_AUDIT_SYSTEM_USER_EMAIL``.
- Dynamic agent execution: ``DYNAMIC_AGENT_PATH``,
  ``DYNAMIC_AGENT_TIMEOUT_MS``.

Startup flow:
1. Load validated runtime settings from process environment.
2. Configure process logging.
3. Build infrastructure adapters (PostgreSQL repository and S3 storage).
4. Build dynamic-analysis runner and application service.
5. Enter the long-lived polling loop.

Observable exit behavior:
- When startup succeeds, execution is delegated to an infinite loop and normal
  process termination is not expected.
- Startup exceptions are not caught in this module; running as ``__main__``
  therefore exits the interpreter with a non-zero status.
"""

from __future__ import annotations

import logging

from worker_dynamic.adapters.db.postgres_repository import PostgresAnalysisRepository
from worker_dynamic.adapters.storage.s3_storage import S3BinaryStorage
from worker_dynamic.analysis.agent_runner import DynamicAgentRunner
from worker_dynamic.app.runner import WorkerRunner
from worker_dynamic.app.services import DynamicAnalysisService
from worker_dynamic.config.settings import WorkerSettings


def configure_logging(level: str) -> logging.Logger:
    """Configure and return the dynamic worker logger.

    Args:
        level: Logging threshold accepted by ``logging.basicConfig``.

    Returns:
        Logger: Named logger used by worker components.

    Side Effects:
        Mutates global logging configuration for the current interpreter process
        via ``logging.basicConfig``.
    """
    logging.basicConfig(level=level, format="%(asctime)s %(levelname)s [dynamic-worker] %(message)s")
    return logging.getLogger("dynamic-worker")


def main() -> None:
    """Build dependencies and start the worker runtime loop.

    The function creates adapters and services from environment-backed settings,
    then hands execution to :class:`worker_dynamic.app.runner.WorkerRunner`.

    Args:
        None.

    Returns:
        None: This function is expected to block indefinitely once the loop
        starts.

    Raises:
        ValueError: If integer-based settings cannot be parsed from environment
            variables.
        Exception: Propagates startup failures from settings loading,
            infrastructure adapter construction, or runner initialization.

    Side Effects:
        Reads process environment, configures process-wide logging,
        initializes database and object-storage clients, and starts a long-lived
        polling loop.
    """
    settings = WorkerSettings.from_env()
    logger = configure_logging(settings.worker_log_level)

    repository = PostgresAnalysisRepository(settings)
    storage = S3BinaryStorage(settings)
    runner = DynamicAgentRunner(
        settings.agent_binary_path,
        settings.agent_timeout_ms,
        settings.correlation_environment_profile,
        {
            "exposure": settings.deployment_exposure,
            "privilegeLevel": settings.deployment_privilege_level,
            "dataSensitivity": settings.deployment_data_sensitivity,
        },
        settings.correlation_observed_runs,
    )

    service = DynamicAnalysisService(
        repository=repository,
        storage=storage,
        runner=runner,
        profile=settings.worker_profile,
        logger=logger,
    )
    app_runner = WorkerRunner(service=service, poll_seconds=settings.worker_poll_seconds, logger=logger)
    app_runner.run_forever()


if __name__ == "__main__":
    main()
