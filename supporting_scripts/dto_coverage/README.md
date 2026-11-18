# DTO Coverage Tooling

This folder contains scripts to measure DTO usage at the API boundary.

## Scripts

- `dto_coverage.py`  
  Scans Spring REST controllers for return types and request bodies and classifies
  endpoints as `dto`, `entity`, or `neutral`(does not transfer structured models or has ambiguous types).

## Quick Start

From the repository root:

```bash
chmod +x dto_coverage.py

python3 dto_coverage.py \
  --root ../.. \
  --base-package de.tum.cit.aet.artemis \
  --module-strategy package \
  --controller-path-hints "src/main/java" \
  --out dto_coverage_report


