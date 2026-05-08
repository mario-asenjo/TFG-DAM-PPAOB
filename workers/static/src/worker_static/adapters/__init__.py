"""Integration adapters for the static analysis worker.

This package contains concrete implementations of outbound ports used by the
static worker. Adapters are responsible for boundary concerns only
(PostgreSQL and S3-compatible storage I/O) and must not contain analysis
business logic.
"""
