"""Static analysis pipeline for pre-exploitation triage.

This package aggregates baseline static analysis components used by the
`worker-static` service:

- ELF structural parsing and mitigation extraction.
- Signal extraction from symbols and embedded string literals.
- Environment-aware correlation and risk scoring.

Contracts in this package intentionally focus on observable indicators from
binary content and deployment context. Results are heuristic and should be
interpreted as prioritization support, not proof of exploitability.
"""
