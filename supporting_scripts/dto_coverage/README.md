# DTO Coverage Tooling

This folder contains scripts to measure DTO usage at the API boundary.

## Scripts

- `dto_coverage.py`  
  Scans Spring REST controllers for return types and `@RequestBody` payloads and classifies
  endpoints as `dto`, `entity`, or `neutral` (does not transfer structured models or has ambiguous types).

## Prerequisites

- Python 3.x installed (`python --version` or `python3 --version`)

## Quick Start

### Run from `supporting_scripts/dto_coverage`

#### PowerShell (Windows)

```powershell
cd .\supporting_scripts\dto_coverage
python dto_coverage.py `
  --root ../.. `
  --out dto_coverage_report `
  --base-package de.tum.cit.aet.artemis `
  --module-strategy package `
  --controller-path-hints "src/main/java" `
  --entity-package-hints "domain,entity"
```

#### Bash (Linux/macOS)

```bash
cd supporting_scripts/dto_coverage
python3 dto_coverage.py \
--root ../.. \
--out dto_coverage_report \
--base-package de.tum.cit.aet.artemis \
--module-strategy package \
--controller-path-hints "src/main/java" \
--entity-package-hints "domain,entity"
```
