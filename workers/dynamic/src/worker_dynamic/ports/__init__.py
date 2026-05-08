"""Application port contracts for the dynamic worker.

This package defines the boundary between dynamic-worker use cases and
infrastructure adapters. Ports in this package describe what the application
layer needs from persistence and object storage without coupling to concrete
DB or SDK implementations.

The contracts are intentionally pre-exploitation scoped: they cover analysis
orchestration metadata, artifact persistence, and input/output object handling
for defensive analysis workflows.
"""
