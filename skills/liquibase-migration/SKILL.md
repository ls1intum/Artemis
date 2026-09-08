---
name: liquibase-migration
description: Write an Artemis Liquibase changelog that applies cleanly on both PostgreSQL and MySQL. Use when adding, changing, or dropping a database column, table, index, or constraint, or when a changeset fails on startup. Covers the file and id conventions, the guarded pattern for adding NOT NULL, expand and contract for a column code still reads, and the local validation steps.
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
releases, so that rolling the application back to the previous version still finds a schema it can
read.

**Anything involving a trigger or a stored routine.** Do not. This repository removed its last
trigger when it moved to PostgreSQL and has rejected proposals to add new ones. Express the
behaviour in the entity design or in application code instead.

## How migrations are deployed

Artemis is **not** deployed as a rolling update. Every instance is stopped, the first instance is
started alone and applies the migrations, and the remaining instances start only once it is up. See
`documentation/docs/admin/production-setup/multiple-artemis-instances.mdx` (Deploying a New Version).

So a changelog never has to be compatible with two versions of the application at once. Do not phase
a migration across releases to keep old nodes working, do not add a column as nullable purely to
make it NOT NULL in the next release, and do not reason about an old node inserting a row without a
newly added column. Add the column, backfill it, and constrain it in one changeset.

The one thing that still argues for two releases is **rollback**: dropping a column that the previous
version reads makes rolling back impossible, which is why expand and contract survives below.

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
