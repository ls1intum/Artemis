---
name: liquibase-migration
description: Write an Artemis Liquibase changelog that applies cleanly on both PostgreSQL and MySQL. Use when adding, changing, or dropping a database column, table, index, or constraint, or when a changeset fails on startup. Covers the file and id conventions, the guarded pattern for adding NOT NULL, expand and contract for a column that code still reads or writes, the rollback invariant, and the local validation steps.
---

# Write a Liquibase migration

A bad changeset does not fail a test, it stops the application from starting, on every node, in
production. Everything here exists because of that.

## The mechanics

New changelogs live in `src/main/resources/config/liquibase/changelog/` and are included from
`src/main/resources/config/liquibase/master.xml`.

1. Get the timestamp: `date '+%Y%m%d%H%M%S'`
2. Create `src/main/resources/config/liquibase/changelog/<timestamp>_changelog.xml`
3. Add an `<include>` line for it at the end of `master.xml`, keeping chronological order

That is the whole job. **Adding a changelog never means touching the baseline**, however close a
release is: a changelog under `changelog/` runs on a fresh installation and on an upgrade alike. Do not
re-run `cut_baseline.py` to fold it in either — re-cutting a generation any database has already
recorded, a deployed test server included, changes checksums those databases refuse to start against.
Cutting is described in
[database-migration-consolidation](../../documentation/docs/developer/guidelines/database-migration-consolidation.mdx).

`master.xml` also lists three directories you do not write into by hand. `baseline/` holds the
generated schema as of the last consolidation, `history/` the changelogs that baseline already folded,
and `data/` the seed data. **Seed data never goes in `changelog/`**: a folded changeset is recorded
rather than executed on a fresh installation, which is right for a schema change the baseline already
contains and wrong for data, whose rows would silently be missing. Anything with a `context` belongs in
`data/`. A changelog **cherry-picked from a release branch** also goes in `changelog/`, even when its
timestamp predates the baseline: under `history/` a fresh installation would record it without executing
it, and the change would simply be missing. `supporting_scripts/liquibase/verify_schema.py --check layout`
enforces the first rule and `--check converge` the second, and
[database-migration-consolidation](../../documentation/docs/developer/guidelines/database-migration-consolidation.mdx)
explains the layout.

Changeset ids are `<timestamp>-<sequence>-<slug>`, for example
`20260827090000-02-result-submission-not-null`. The author is your username. Never edit the *changes* of a changeset
that has already been merged: Liquibase records a checksum over the forward change elements and any
`modifySql`, and the application refuses to start when it no longer matches. Write a new changeset
instead. Comments are not part of that checksum, so an XML comment, a `<comment>` element and a
`<rollback>` body can be corrected on a merged changelog — and a `<rollback>` sometimes has to be,
since it is read from the file when a rollback runs rather than from the database, so a stale one
recovers nothing while appearing to work.

Read `reference/migration-patterns.md` for the worked patterns. The rest of this file is the
decision procedure.

## Which pattern do you need?

**Adding a nullable column, a table, or an index.** Straightforward. Write the changeset, add a
`<rollback>` if Liquibase cannot infer one.

**Adding a NOT NULL constraint to an existing column.** Use the guarded pattern. Adding the
constraint while a null is still present fails the changeset, and a failing changeset stops the
application from starting. This is the single most dangerous migration in this codebase and the
pattern is non-obvious, so read the section in `reference/migration-patterns.md` before writing it.

**Dropping or renaming a column that code still references.** Use expand and contract across two
releases, so that rolling the application back to the previous version still finds a schema it can
read and write. Reading is not the only way to depend on a column: one that the previous version never
selects can still appear in the `INSERT` and `UPDATE` statements it issues, and those fail just as
hard once the column is gone or renamed.

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

The one thing that still argues for two releases is **rollback**, and it argues for more than
expand and contract. Reverting to the previous WAR does not revert the schema, so the invariant to
check is: does the new schema still accept everything the previous version reads and writes?

- Dropping or renaming a column that the previous version reads or writes breaks it outright. This is why
  expand and contract survives below.
- Adding a `NOT NULL` column without a default breaks every `INSERT` the previous version issues
  against that table, even though it never mentions the column.
- Tightening a constraint, a length or a uniqueness rule makes the previous version's writes fail
  whenever they were only valid under the old rule.

The last two are additions, so they look safe and are not. If your migration breaks the invariant,
say so in the pull request and name the way back: a backup taken immediately before the migration
and restored as part of the rollback, or a backward-compatible schema now and the tightening in a
later release. What is not acceptable is discovering at rollback time that there is no way back.

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

Then run the schema checks, which are what CI runs. They need Docker and take about two minutes:

```bash
python3 supporting_scripts/liquibase/verify_schema.py
```

They apply the changelogs to empty MySQL and PostgreSQL databases and compare the fresh-installation
and upgrade routes. Empty is the operative word: they verify structure, not the backfill your
changeset performs, which is why the paragraph above about starting against a database that already
has data is the part that cannot be automated away.

## Related

Entity-side rules, including why adding a NOT NULL to a column held by a cascading collection
fails, are in `skills/server-arch-gates/SKILL.md`.
