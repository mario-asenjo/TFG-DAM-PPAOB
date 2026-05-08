"""Application orchestration for the static analysis worker.

This package contains the app-layer components that compose worker ports,
analysis services, and the long-running polling loop. It coordinates job
lifecycle transitions (claim, process, mark done/failed) without owning
infrastructure details or analysis-rule definitions.

The package is intentionally orchestration-only: repository/storage adapters,
domain model definitions, and signal extraction logic are delegated to sibling
modules.
"""
