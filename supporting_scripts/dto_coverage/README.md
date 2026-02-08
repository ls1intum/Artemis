# DTO Coverage Tooling

This folder contains scripts to measure DTO usage at the API boundary.

## Scripts

- `dto_coverage.py`  
  Scans Spring REST controllers for return types and `@RequestBody` payloads and classifies
  endpoints as:

    - `dto`: request body uses DTOs (and does not mix with Entities across request/response)
    - `entity`: request body uses Entities (and does not mix with DTOs across request/response)
    - `mixed`: DTOs and Entities are mixed across request/response (e.g., request DTO + response Entity, or vice versa)
    - `neutral`: request body transfers **neither** DTO nor Entity (return type is ignored for this classification)

### Coverage Metric

The tool reports **Coverage** as:

> `Coverage = (DTO + Mixed + Neutral) / All endpoints`

This matches the definition “at least one side uses DTO OR the request body does not transfer structured models”.

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
