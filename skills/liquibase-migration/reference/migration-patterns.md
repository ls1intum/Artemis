# Liquibase migration patterns

## Adding a NOT NULL constraint to an existing column

`addNotNullConstraint` fails while existing rows contain nulls. Check the data and entity mapping
before choosing a backfill, deletion, or deferred constraint.

Repository example: `src/main/resources/config/liquibase/changelog/20260827090000_changelog.xml`
separates orphan cleanup from guarded constraint changesets. Read the complete dependency order;
its cleanup is specific to those relationships, not a deletion template. Delete data only under
a reviewed policy for the affected records and their dependants. A missing parent alone does not
establish that the remaining data is disposable.

Choose the precondition failure behaviour according to the application's schema requirements:

- `CONTINUE` skips the changeset without marking it executed and retries it on a later update.
  Use it only when the application remains correct without the constraint. Verify whether it
  actually ran; startup success does not establish the constraint.
- `HALT` stops the update. Use it when continuing without the constraint would violate an
  application invariant, with cleanup or backfill completed before deployment.
- `MARK_RAN` records a skipped changeset as executed. Do not use it to defer a required constraint;
  a later update will not retry that changeset.

Keep unrelated constraint changes separate so that a precondition for one does not skip the others.
If cleanup requires application deletion semantics, use the relevant service rather than duplicating
partial cleanup in SQL. Do not add the constraint until both the data and write paths support it.

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
