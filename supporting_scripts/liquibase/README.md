# Liquibase schema tooling

Two tools. `cut_baseline.py` folds the changelogs into a new baseline; `verify_schema.py` is what
establishes that the result is correct. Neither is a substitute for the other, and the second is not
optional after the first.

The layout they operate on, and the reasoning behind it, is documented in
[database-migration-consolidation.mdx](../../documentation/docs/developer/guidelines/database-migration-consolidation.mdx).
This file covers only how to run them.

## Verifying

```bash
python3 verify_schema.py                                   # six checks, both databases, ~4 minutes
python3 verify_schema.py --check layout                    # source only, no Docker
python3 verify_schema.py --check converge --database postgres
```

Needs Docker. The JDBC drivers come from `./gradlew exportLiquibaseDrivers`, which the script runs
for you if `build/liquibase-drivers` is missing, so that the checks connect with the driver versions
the application uses rather than whatever is newest.

Containers bind ports from 15400 upwards; use `--base-port` or `LIQUIBASE_VERIFY_BASE_PORT` if
something else already has them. Each check starts its own containers and removes them afterwards,
including when it fails.

## Cutting a baseline

```bash
python3 cut_baseline.py --generation v11
python3 verify_schema.py
```

`--dry-run` writes the baseline without moving anything, which is the way to inspect the output before
committing to the file moves.

The script stops and changes nothing if it meets a change it cannot fold, listing each one. Two
reasons are common:

- **A changeset gated by a context.** Move it to `config/liquibase/data/`, pinning `logicalFilePath`
  to the file it came from so the changeset keeps the identity every database recorded it under.
- **A change element with no rule for it.** Add one to `schema_state.py`. Refusing is deliberate:
  ignoring a change silently produces a baseline that is wrong in a way the converge check can only
  report as a mystery, and stopping names the changeset instead.

## Files

| File | Role |
|------|------|
| `cut_baseline.py` | Folds changelogs into a baseline, files them under `history/`, rewrites `master.xml` |
| `schema_state.py` | The schema model the fold replays change elements onto |
| `verify_schema.py` | The six checks, and the container and Liquibase plumbing behind them |
| `db_schema.py` | Reads a live schema into a canonical form two databases can be compared in |
