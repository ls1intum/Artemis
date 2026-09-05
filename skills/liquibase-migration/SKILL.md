---
name: liquibase-migration
description: Write an Artemis Liquibase changelog that survives a rolling deployment on both PostgreSQL and MySQL. Use when adding, changing, or dropping a database column, table, index, or constraint, or when a changeset fails on startup. Covers the file and id conventions, the guarded pattern for adding NOT NULL, expand and contract for rolling deploys, and the local validation steps.
---

# Write a Liquibase migration

A bad changeset does not fail a test, it stops the application from starting, on every node, in
production. Everything here exists because of that.

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

Read `reference/migration-patterns.md` for the worked patterns. The rest of this file is the
decision procedure.

## Which pattern do you need?

**Adding a nullable column, a table, or an index.** Straightforward. Write the changeset, add a
`<rollback>` if Liquibase cannot infer one.

**Adding a NOT NULL constraint to an existing column.** Use the guarded pattern. Adding the
constraint while a null is still present fails the changeset, and a failing changeset stops the
application from starting. This is the single most dangerous migration in this codebase and the
pattern is non-obvious, so read the section in `reference/migration-patterns.md` before writing it.

**Dropping or renaming a column that code still reads.** Use expand and contract across two
releases. During a rolling deployment, nodes on the old version are still running.

**Anything involving a trigger or a stored routine.** Do not. This repository removed its last
trigger when it moved to PostgreSQL and has rejected proposals to add new ones. Express the
behaviour in the entity design or in application code instead.

## Both databases

Artemis runs on PostgreSQL and MySQL. CI tests PostgreSQL. Production configuration hardcodes the
PostgreSQL dialect with no probing, so a MySQL deployment must override `spring.jpa.database`.

Consequences when writing a changeset:

- Prefer Liquibase's own change types over `<sql>`. They generate correct SQL for both.
- Where you must write raw SQL, check it against both dialects, or split it with a `dbms` attribute.
- CI will not catch a MySQL-only break. If a changeset contains raw SQL, validate it locally
  against MySQL. `reference/migration-patterns.md` has the procedure.

## Verify before pushing

Start the application against a database that already has data, not an empty one. An empty database
makes every backfill and every precondition trivially pass, which is exactly the case that is never
interesting.

```bash
./gradlew bootRun -x webapp
```

Watch the startup log for the changeset ids. A changeset skipped by a precondition logs a warning
rather than failing, so a silent skip is easy to miss.

## Related

Entity-side rules, including why adding a NOT NULL to a column held by a cascading collection
fails, are in `skills/server-arch-gates/SKILL.md`.
