"""Long-lived runtime loop for dynamic-analysis orchestration.

The runner is intentionally small: it delegates one processing attempt to the
application service, applies polling backoff when no work is available, and
keeps the process alive after unexpected errors.
"""

from __future__ import annotations

import logging
import time

from worker_dynamic.app.services import DynamicAnalysisService


class WorkerRunner:
    """Execute the dynamic-worker polling loop.

    The class owns loop lifecycle concerns (sleeping and top-level exception
    shielding) and leaves job-specific behavior to ``DynamicAnalysisService``.
    """

    def __init__(self, service: DynamicAnalysisService, poll_seconds: int, logger: logging.Logger) -> None:
        """Initialize loop dependencies.

        Args:
            service: Application service that performs one claim/process cycle.
            poll_seconds: Delay applied when no job is processed or after loop
                errors.
            logger: Logger used for lifecycle and failure events.
        """
        self._service = service
        self._poll_seconds = poll_seconds
        self._log = logger

    def run_forever(self) -> None:
        """Run the worker loop indefinitely.

        On each iteration, the runner asks the service to process at most one
        job. If no job is available, the loop sleeps for ``poll_seconds``.
        Unexpected exceptions are logged and also followed by a sleep, so the
        process continues operating instead of terminating.

        Side Effects:
            Writes operational logs and sleeps between polling iterations.
        """
        self._log.info("Dynamic worker started")
        while True:
            try:
                processed = self._service.process_once()
                if not processed:
                    time.sleep(self._poll_seconds)
            except Exception as loop_error:
                self._log.exception("Dynamic worker loop error: %s", loop_error)
                time.sleep(self._poll_seconds)
