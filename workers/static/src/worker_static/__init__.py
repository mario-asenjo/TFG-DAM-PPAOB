"""Top-level package for the static-analysis worker.

This package groups the worker components that poll analysis jobs, run the
static-analysis pipeline over submitted binaries, and persist pre-exploitation
analysis outputs through configured ports/adapters.

Public surface:
- package namespace only (no symbols are re-exported here);
- consumers should import concrete modules from ``worker_static.cli``,
  ``worker_static.app``, ``worker_static.analysis``, ``worker_static.ports``,
  ``worker_static.adapters``, ``worker_static.config``, or
  ``worker_static.domain`` as needed.

Responsibility boundaries:
- this module provides package metadata/documentation only;
- it does not perform runtime initialization, filesystem I/O, network calls,
  subprocess execution, or persistence operations on import.
"""
