---
name: liquibase-migration
description: Write Artemis database schema migrations or diagnose Liquibase changesets that fail on startup.
---

# Write a Liquibase migration

## The mechanics

Changelogs live in `src/main/resources/config/liquibase/changelog/` and are included from
`src/main/resources/config/liquibase/master.xml`.

1. Get the timestamp: `date '+%Y%m%d%H%M%S'`
2. Create `src/main/resources/config/liquibase/changelog/<timestamp>_changelog.xml`
3. Add an `<include>` line for it at the end of `master.xml`, keeping chronological order

Changeset ids are `<timestamp>-<sequence>-<slug>`, for example
`20260827090000-02-result-submission-not-null`. The author is your username. Never edit a changeset
that has already been merged: Liquibase records a checksum and the application refuses to start
when it changes. Write a new changeset instead.

Read the relevant pattern in `reference/migration-patterns.md`.

## Which pattern do you need?

**Adding a nullable column, a table, or an index.** Add a
`<rollback>` if Liquibase cannot infer one.

**Adding a NOT NULL constraint to an existing column.** Check the entity mapping and existing
nulls before adding the constraint. Read the precondition guidance in `reference/migration-patterns.md`;
a precondition that skips a constraint does not establish the intended schema invariant.

**Dropping or renaming a column that code still reads.** Use expand and contract across two
releases. During a rolling deployment, nodes on the old version are still running.

**Triggers and stored routines.** Keep this behaviour in entity design or application code;
do not introduce database triggers or stored routines.

## Both databases

Artemis runs on PostgreSQL and MySQL. CI tests PostgreSQL. Production configuration hardcodes the
PostgreSQL dialect with no probing, so a MySQL deployment must override `spring.jpa.database`.

Consequences when writing a changeset:

- Prefer Liquibase's built-in change types over `<sql>` for dialect handling.
- Where you must write raw SQL, check it against both dialects, or split it with a `dbms` attribute.
- CI will not catch a MySQL-only break. If a changeset contains raw SQL, validate it locally
  against MySQL. `reference/migration-patterns.md` has the procedure.

## Verify before pushing

Verify migrations on a disposable database containing representative existing data, including
rows affected by backfills and preconditions. Never use a production database for this check.
An empty-database startup alone does not exercise these cases.

```bash
./gradlew bootRun -x webapp
```

Inspect the resulting schema and data, not just startup success. Check the changeset ids in
the logs and `databasechangelog`; preconditions can skip a changeset without failing startup.

## Related

Entity-side rules, including why adding a NOT NULL to a column held by a cascading collection
fails, are in `skills/server-arch-gates/SKILL.md`.
