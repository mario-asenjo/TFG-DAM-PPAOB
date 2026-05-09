"""Runtime bootstrap and polling loop orchestration for the static worker.

The runner owns process-lifetime concerns: startup logging, infinite polling,
sleep cadence when no jobs are available, and loop-level exception shielding.
It delegates claim/processing/persistence of individual jobs to the app service.
"""

from __future__ import annotations

import logging
import time

from worker_static.app.services import StaticAnalysisService


class WorkerRunner:
    """Runs the static worker polling loop around ``StaticAnalysisService``.

    Responsibility boundary:
    - runner: process lifecycle and polling/backoff cadence,
    - service: one-job claim/process/mark transition orchestration.
    """

    def __init__(
        self,
        service: StaticAnalysisService,
        poll_seconds: int,
        logger: logging.Logger,
    ) -> None:
        """Initialize loop dependencies used for long-running execution.

        Args:
            service: App service that processes at most one job per iteration.
            poll_seconds: Delay used after idle iterations and loop errors.
            logger: Logger for lifecycle events and unexpected loop failures.
        """
        self._service = service
        self._poll_seconds = poll_seconds
        self._log = logger

    def run_forever(self) -> None:
        """Run the worker loop indefinitely.

        Each iteration attempts one ``process_once`` call. When no job is
        available, the loop sleeps ``poll_seconds`` before retrying. Any
        uncaught loop-level exception is logged and followed by the same sleep
        interval so the process remains alive.

        Returns:
            None: This method is intentionally non-returning during normal
            operation.

        Side Effects:
            Emits operational logs and blocks the current thread with periodic
            ``time.sleep`` calls.
        """
        self._log.info("Static worker started")
        while True:
            try:
                processed = self._service.process_once()
                if not processed:
                    time.sleep(self._poll_seconds)
            except Exception as loop_error:
                self._log.exception("Worker loop error: %s", loop_error)
                time.sleep(self._poll_seconds)
