# Liquibase migration patterns

Worked patterns with the reasoning. Real examples from this repository are cited so you can read
the full changeset rather than a fragment.

## Adding a NOT NULL constraint to an existing column

The problem: `addNotNullConstraint` fails if a single row still holds a null, and a failing
changeset stops the application from starting. On a production database you cannot know in advance
that no such row exists.

The pattern is two changesets per column: one that clears the rows without a parent, and one that
adds the constraint behind a precondition.

The snippet below is **abridged to show the shape**. Do not copy it as a template: the real
changeset also clears `long_feedback_text`, `text_block`, `result_rating`, `assessment_note`,
`complaint_response` and `complaint`, and nulls three foreign key columns, in that order. Deleting
a parent before its dependants dies on a foreign key constraint, which is exactly the
application-will-not-start failure this skill opens with. Work out the full dependency order for
your own table, and read the complete changeset cited below.

```xml
<changeSet id="20260827090000-01-delete-results-without-submission" author="krusche">
    <comment>Remove results that belong to no submission, along with everything that hangs off them.</comment>
    <sql>
        <!-- abridged: the real changeset clears six more tables and nulls three FK columns first -->
        DELETE FROM feedback WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL);
        DELETE FROM result WHERE submission_id IS NULL;
    </sql>
</changeSet>
<changeSet id="20260827090000-02-result-submission-not-null" author="krusche">
    <preConditions onFail="CONTINUE">
        <sqlCheck expectedResult="0">SELECT COUNT(*) FROM result WHERE submission_id IS NULL</sqlCheck>
    </preConditions>
    <addNotNullConstraint tableName="result" columnName="submission_id" columnDataType="bigint"/>
</changeSet>
```

Three things about this are deliberate and easy to get wrong.

**`onFail="CONTINUE"`, never `MARK_RAN`.** `CONTINUE` skips the changeset without recording it in
`databasechangelog`, so the server starts, a warning is logged, and the constraint is attempted
again on the next startup. Once the offending rows are gone the column constrains itself.
`MARK_RAN` would record the changeset as done, and that installation would keep a nullable column
for good, fixable only by a new changelog.

**One pair of changesets per column.** Keeping them separate means a single column can be dropped
from the migration without disturbing the others.

**Only delete rows whose own dependants you can clear.** Where a row is reachable through several
other tables, deleting it in a changelog is the wrong place; that belongs in the relevant deletion
service. Leave the column nullable and say why in the comment.

Full example, including the reasoning for each column that was deliberately left out:
`src/main/resources/config/liquibase/changelog/20260827090000_changelog.xml`.

### When the entity mapping blocks it

Some columns cannot be made NOT NULL without an application change first. If the column is the
owning side of an association held by the parent in a `mappedBy` collection that cascades,
Hibernate inserts the child before it writes the key and fills it in with a later update. That is
invisible while the column allows null and fails immediately once it does not.

Making such a column NOT NULL means giving every place that adds to the collection the back
reference first, in the `add*` helper on the parent. Roughly fifty owning associations in this
codebase sit behind a cascading collection, so check the mapping before assuming a column is a
simple case.

## Expand and contract

For dropping or renaming a column that code still reads, split the work across two releases,
because during a rolling deployment nodes on the previous version are still serving traffic.

1. **Expand.** Create the new structure, backfill it, and move every reader and writer across.
   Leave the old column in place, still populated.
2. **Contract.** Once the expand release is fully deployed, stop mapping the old column, then drop
   it in a later release.

The contract changelog must state that it must not be deployed before its predecessor.

Rollback deserves thought here. Re-creating a dropped column without its values is often worse than
having no rollback, because code would silently read empty columns. Where the data now lives
elsewhere, say so in the rollback comment and rely on rolling the predecessor back instead.

Worked example: `src/main/resources/config/liquibase/changelog/20260826080000_changelog.xml`.

## Rollbacks

Liquibase infers a rollback for most of its own change types. Write an explicit `<rollback>` when:

- the change is raw `<sql>`
- the change drops something, so the inferred rollback would be wrong or impossible
- the inferred rollback would restore structure without data

## Validating against MySQL locally

CI runs PostgreSQL. A MySQL-only break therefore reaches production undetected. Validate locally
whenever a changeset contains raw SQL or a type whose spelling differs between the two.

The approach that works: seed a MySQL database using a checkout of develop, so it has the schema as
it exists before your change, then start your branch against that same database and let Liquibase
migrate it. Compare the resulting schema against one built from scratch. A migration that produces
a different schema than a fresh install is a bug, and it is the class of bug this check exists to
find.

Remember that a MySQL deployment needs `spring.jpa.database` overridden, since the production
configuration hardcodes the PostgreSQL dialect.

## Things that are not allowed

**Triggers and stored routines.** The last trigger was removed when PostgreSQL support arrived, and
proposals to add new ones have been rejected. Express it in the entity design instead.

**Editing a merged changeset.** Liquibase stores a checksum. Changing the file makes every existing
installation refuse to start. Add a new changeset.

**Assuming an empty database.** Test against data. Every precondition and backfill passes trivially
on an empty schema, which is the one case that never matters.
