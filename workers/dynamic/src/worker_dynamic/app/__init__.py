"""Application layer that orchestrates one dynamic-analysis worker loop.

This package contains orchestration-only components for the dynamic worker:

- dependency composition inputs (repository, storage, agent runner),
- polling/claim/processing control flow,
- state transitions for completed or failed analyses.

The package does not implement agent internals, storage protocols, or
database adapters. Those responsibilities live behind ports and adapters.
"""
