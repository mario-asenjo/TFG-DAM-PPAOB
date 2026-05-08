"""Top-level package for the dynamic-analysis worker.

This package groups the worker components that poll analysis jobs, execute the
dynamic-analysis pipeline, and persist pre-exploitation telemetry artifacts via
configured ports/adapters.

Public surface:
- package namespace only (no symbols are re-exported here);
- consumers should import concrete modules from ``worker_dynamic.cli``,
  ``worker_dynamic.app``, ``worker_dynamic.analysis``, ``worker_dynamic.ports``,
  ``worker_dynamic.adapters``, ``worker_dynamic.config``, or
  ``worker_dynamic.domain`` as needed.

Responsibility boundaries:
- this module provides package metadata/documentation only;
- it does not perform runtime initialization, I/O, network calls, or
  persistence operations on import.
"""
