"""Application port contracts for the static worker.

This package defines the inbound-facing contracts that decouple application
services from infrastructure adapters (database and object storage).
Implementations are provided by adapters under ``worker_static.adapters``.

The contracts in this package describe observable behavior only:
- what data each operation consumes/produces,
- which lifecycle transitions are expected,
- and which external side effects are delegated to implementations.
"""
