# Liquibase migration patterns

## Adding a NOT NULL constraint to an existing column

`addNotNullConstraint` fails while existing rows contain nulls. Check the data and entity mapping
before choosing a backfill, deletion, or deferred constraint.

Repository example: `src/main/resources/config/liquibase/changelog/20260827090000_changelog.xml`
separates orphan cleanup from guarded constraint changesets. Read the complete dependency order;
its cleanup is specific to those relationships, not a deletion template. Delete data only under
a reviewed policy for the affected records and their dependants. A missing parent alone does not
establish that the remaining data is disposable.

Use the repository's guarded pattern: one cleanup changeset and one constraint changeset per
column, with `onFail="CONTINUE"` on the null-count precondition. This keeps the application
starting if rows still prevent the constraint. `CONTINUE` leaves the changeset unrecorded and
retries it on a later update; `MARK_RAN` would record it as executed and prevent that retry.
Verify whether the constraint actually ran; startup success alone does not establish it.

Keep unrelated constraint changes separate. Leave a column nullable when its data or write paths
are not ready, and explain the specific prerequisite in the changelog. If cleanup requires
application deletion semantics, use the relevant service rather than duplicating partial cleanup
in SQL. Do not make application correctness depend on a constraint that can still be deferred.

### When the entity mapping blocks it

For a bidirectional association, updating only a parent's `mappedBy` collection does not set the
child's owning-side foreign key. Cascading persistence can then attempt an insert with a null key.
Check the `add*` helpers and every creation path; set the child's back reference before persistence
and verify the insert against the proposed constraint.

## Expand and contract

During a rolling deployment, old and new nodes share the database. Keep the schema compatible
with both versions until old readers and writers are retired.

1. **Expand.** Add the new structure and backfill it. Migrate readers and writers while keeping
   the old representation populated for old nodes, including writes during the rollout.
2. **Contract.** Stop mapping the old structure. Drop it only after every version that accesses
   it is retired and the rollback window permits removal.

Document deployment prerequisites in the contract changelog. Re-creating a dropped column does
not restore its data; do not present a structure-only rollback as data recovery. Verify the data
restoration and application-version requirements before offering a rollback procedure.

Worked example: `src/main/resources/config/liquibase/changelog/20260826080000_changelog.xml`.

## Rollbacks

Check whether Liquibase supports automatic rollback for the chosen change type. Supply an explicit
rollback for raw SQL or unsupported changes when reversal is possible. For destructive changes,
document the recovery prerequisite instead of inventing a rollback that silently loses data.

## Validating against MySQL locally

CI runs PostgreSQL and does not validate MySQL-specific SQL. Validate raw SQL and database-specific
types against MySQL as well.

Use a disposable MySQL database seeded from the branch's base revision, with representative data.
Start the changed branch against it and inspect the migrated schema and data. Compare the schema
with a fresh installation of the changed branch and investigate differences. Never run this check
against a production database.

A MySQL deployment needs `spring.jpa.database` overridden because production configuration selects
the PostgreSQL dialect.

## Constraints

- Keep behaviour in entity design or application code, not triggers or stored routines.
- Do not edit merged changesets; Liquibase validates their stored checksums. Add a new changeset.
- Exercise affected rows and precondition outcomes; an empty-database startup does not test
  backfills or migration of existing data.
