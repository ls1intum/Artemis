# Production Schema Drift — Findings, Fixes, Risk & Effort

**Date:** 2026-07-28
**Production host:** `db.production.artemis.cit.tum.de` (MySQL 8.0.46, schema `Artemis`)
**Production Artemis version:** 9.7.1 · **Code version (`develop`):** 9.8
**Last Liquibase run in production:** 2026-07-16 08:05:07

> **Status:** **P2, P3, P4 and P6 have been applied to production** on 2026-07-28 and verified — see the status log below. Production-side DDL is now complete apart from P1 (pending, ships via Liquibase), P5/P8 (accepted divergence) and P7 (blocked on a retention decision).
>
> ⚠️ **All four were applied by hand and none is recorded in `DATABASECHANGELOG`.** Writing the precondition-guarded changesets is now the top outstanding task — see §5.

### Status log

| Item | Status | Verified |
|---|---|---|
| **P1** — team/student invariant | Not applied. **Changeset written and verified on MySQL 8.0.46 + PostgreSQL 17.10** (apply/rollback/re-apply/idempotency + concurrent-race test all pass) | See P1 → *Verification* |
| **P2** — temporal precision on 6 columns | **✅ Applied to production 2026-07-28** | All six columns report `DATETIME_PRECISION = 3`; nullability preserved (`science_event.timestamp` and `migration_changelog.date_executed` remain `NOT NULL`). Changeset work still outstanding — see P2. |
| **P3** — drop 3 redundant indexes | **✅ Applied to production 2026-07-28** | All three gone; every covering index intact. `information_schema.STATISTICS` for the three tables is now **byte-identical** to a fresh Liquibase install. Changeset work still outstanding — see P3. |
| **P6** — collation fork | **✅ Applied to production 2026-07-28** | Both columns now `utf8mb4_unicode_ci`. The **entire** schema is now uniformly `utf8mb4_unicode_ci` — all 384 character columns, zero exceptions. No changeset needed; see P6 for the one residual gap. |
| **P4** — unique-constraint names | **✅ Applied to production 2026-07-28** (after a partial failure + repair — see P4) | One UNIQUE index per column, all named `uk_*`; all 3 foreign keys intact. Changeset work still outstanding — the changelog must declare these names or fresh installs keep auto-naming after the column. |
| All other items (P5, P7, P8, C1–C8) | Not applied | — |

### Open pull requests

| PR | Covers | State |
|---|---|---|
| [#13329](https://github.com/ls1intum/Artemis/pull/13329) | Changesets recording the completed production work: **P2**, **P3**, **P4**, **C1** | open |
| [#13330](https://github.com/ls1intum/Artemis/pull/13330) | **P1** — team/student invariant, enforced by a denormalised unique index | open |
| [#13334](https://github.com/ls1intum/Artemis/pull/13334) | **C5** — drop 14 orphaned columns + 4 orphaned tables (incl. the Iris staged deletion). Closes #12807 | open |

Verification approach shared by all three: run the full `master.xml` on MySQL 8.0.46 (production collation) **and** PostgreSQL 17.10, across fresh / production-like / legacy database states, then assert convergence, `EXECTYPE`, rollback and idempotency.

### Remaining drift — re-measured 2026-07-28, after P2, P3 and P6

Full `information_schema` comparison re-run against production. **Every P2, P3 and P6 difference is gone.** What is left, in full:

| Category | Items | Tracked as |
|---|---|---|
| **Pending deploy** (prod 9.7.1 → code 9.8) | `answer_post` ×4 columns + FK + 2 indexes · `attachment.sha256_hash` · `iris_lecture_unit_sync_state` table + 4 indexes + FK · `course.student_course_analytics_dashboard_enabled` still present | §2 — expected lag, applies itself |
| **Unique-constraint names** | ✅ prod done; changelog must declare the 4 `uk_*` names | **P4** |
| **Composite PK column order** | `course_organization`, `user_organization`, `programming_exercise_task_test_case`, `push_notification_device_configuration` (+ 2 FK indexes fresh installs carry and prod does not) | **P5** (deferred) |
| **Missing unique constraint in the changelog** | `result_rating.result_id` — prod has it, code does not | **C1** |
| **Accepted divergence** | 86 boolean `bit(1)` columns · 132 column ordinal positions | **P8** / appendix |

Nothing unexpected appeared, and there are **no remaining foreign-key, column-type or collation differences** outside the pending deploy. After P4 and C1 land, the only intentional divergence left is P5 and P8.

---

## 1. Scope and method

Three sources were compared pairwise:

| Source | How it was obtained |
|---|---|
| **PROD** | `information_schema` + `mysqldump --no-data` against production (read-only access only) |
| **CODE** | `master.xml` executed by Liquibase 5.0.3 against a clean MySQL 8.0.46 container, configured with production's `utf8mb4` / `utf8mb4_unicode_ci` defaults |
| **ANNOTATIONS** | Hibernate 7 mapping model built from all 244 annotated classes using Spring Boot 4's naming strategies (metadata only, no `SessionFactory`) |

Compared: columns (type, nullability, default, collation, `EXTRA`), indexes (uniqueness, column order, sub-parts), foreign keys (including `ON DELETE` / `ON UPDATE`), table engine/collation/row-format, triggers, routines, generated columns, and `DATABASECHANGELOG` history.

Two reporting artifacts were normalized because they are **not** real differences:

- InnoDB reports `NO ACTION` and `RESTRICT` interchangeably across 8.0 minor versions (they are the same behaviour).
- MySQL 8 no longer reports integer display widths (`tinyint(3)` → `tinyint`).

### Root cause of essentially all drift

`00000000000000_initial_schema.xml` describes **what a fresh installation gets**. Production is a ~2016 installation that evolved through changelogs which were later deleted by the schema consolidations (`ed4696bcb3`, `8723fc7290`, `5c7250b2a6`, `15e2f9f3e1` / PR #12401). No changeset was ever written to reconcile *existing* installations with the consolidated schema.

`src/main/resources/config/liquibase/consolidate-changelogs.sh` verifies new-vs-develop equivalence **on fresh databases**, so it structurally cannot detect this class of drift.

### Production host capacity (drives the time estimates)

```
6 cores · 15 GB RAM (12 GB in use) · 93 GB disk, 35 GB free · DB total 35.1 GB
```

35 GB free disk is the binding constraint for any `ALGORITHM=COPY` rebuild: MySQL needs free space roughly equal to the table being rebuilt.

### Relevant MySQL 8.0 online-DDL behaviour

| Operation | Algorithm | Rebuilds table | Concurrent DML | Practical impact |
|---|---|---|---|---|
| `DROP INDEX` | INPLACE | No | **Yes** | seconds, size-independent |
| `ADD UNIQUE INDEX` | INPLACE | No | **Yes** | scales with size, non-blocking |
| `MODIFY COLUMN` (type/precision) | **COPY** | Yes | **No — writes blocked** | scales with size, needs a window |
| `MODIFY COLUMN` (collation) | **COPY** | Yes | **No — writes blocked** | scales with size, needs a window |
| `MODIFY COLUMN … NOT NULL` | INPLACE | Yes | **Yes** | scales with size, non-blocking |
| `DROP COLUMN` | INSTANT (8.0.29+) | No | Yes | milliseconds |
| `DROP TRIGGER` / `DROP TABLE` | — | — | — | milliseconds |

> **Cluster note:** Liquibase runs at application startup while holding `DATABASECHANGELOGLOCK`. Any long-running changeset delays startup of **every** node in the Hazelcast cluster. Changesets estimated above ~30 s should be executed in a maintenance window ahead of the deploy (see §6).

---

## 2. What is *not* a problem

Confirmed clean — no action required:

- **`DATABASECHANGELOG` is consistent.** 53 recorded changesets, **zero** orphan history rows. The 9.0 consolidation was applied correctly.
- **No missing tables or columns** relative to the annotation model (other than the pending deploy, below).
- **No foreign-key differences** — no missing FKs, no differing `ON DELETE` / `ON UPDATE` rules.
- **No column type differences** other than the six temporal-precision cases in P2.
- **No nullability differences between PROD and CODE.**
- **No views, stored routines, or generated columns** in production.
- All 166 tables are InnoDB / `Dynamic` row format on both sides.
- The 8 `e2e-seed-*` changesets are correctly excluded from production by `spring.liquibase.contexts: prod` (`src/main/resources/config/application.yml:287`).

### Pending deploy — expected lag, no action

Four changelog files are in `master.xml` but not yet applied. They will run automatically on the next deploy. All are cheap:

| Changeset | Effect | Table size | Est. duration |
|---|---|---|---|
| `20260702120000-1` | `attachment.sha256_hash` | 4,149 rows / 2 MB | < 1 s (INSTANT) |
| `20260702130000-1` | create `iris_lecture_unit_sync_state` | new | < 1 s |
| `20260718160235-1` | drop `course.student_course_analytics_dashboard_enabled` | 473 rows / 2 MB | < 1 s (INSTANT) |
| `20260720120000-1` | 4 columns + FK + index on `answer_post` | 18,958 rows / 8 MB | ~1 s |

**Total added deploy time: under 5 seconds.**

---

# PART 1 — Production SQL fixes

> **Delivery method.** With one exception (**P2-a**), every fix below must ship as a **precondition-guarded Liquibase changeset**, not as hand-run SQL. Production and fresh installations differ, so an unguarded change will fail on one of them. The SQL shown is what will actually execute *on production*; preconditions make it a no-op elsewhere.
>
> **P2-a (`science_event`) is the exception** — see §6 for why it should be pre-executed manually in a maintenance window and then marked as run.

---

## P1 — Enforce "one team per student per exercise" in the schema

**Priority: HIGHEST** (the only finding with behavioural consequences)

> **Two revisions to earlier drafts of this document, both driven by testing rather than reasoning:**
>
> 1. The first draft proposed **dropping** the production trigger because "the application already enforces the invariant". That was wrong — the application check is a check-then-act race (see below).
> 2. The second draft proposed **re-declaring the trigger with a `FOR UPDATE` locking read** to close that race. That was also wrong on two counts, both verified experimentally: `FOR UPDATE` on the trigger's own table is **rejected by MySQL** (`ERROR 1442`), and **no trigger-based approach closes the race at all**.
>
> The recommendation below is the version that was actually tested end to end.

### What exists today

Production contains a `BEFORE INSERT` trigger that exists in **no changelog** and in **no other environment** — not in CI, not in local dev, not in PostgreSQL:

```
Trigger:  uc_team_student_exercise_id_and_student_id
Table:    team_student   (198,586 rows / 16 MB)
Timing:   BEFORE INSERT
Action:   SIGNAL SQLSTATE '45000' if the student already belongs to a different
          team for the same exercise
Enforces: a student may belong to at most one team per exercise
```

It originated in PR #1302 / #1385 (2020) and was **deleted from the changelogs** by `ed4696bcb3` ("Add PostgreSQL and test container support and cleanup change logs") — almost certainly because it has no PostgreSQL equivalent. Production kept it; every environment created after that commit does not have it.

**Current production data: 0 violating (exercise, student) pairs.** No cleanup is needed before adding a constraint.

### Why the application check is not an invariant guard

`TeamRepository.save(Exercise, Team)` is a textbook time-of-check-to-time-of-use race:

```java
default Team save(Exercise exercise, Team team) {
    List<Pair<User, Team>> conflicts = findStudentTeamConflicts(exercise, team);  // READ
    if (!conflicts.isEmpty()) {
        throw new StudentsAlreadyAssignedException(conflicts);
    }
    team.setExercise(exercise);
    team = save(team);                                                            // WRITE
    return findWithStudentsByIdElseThrow(team.getId());
}
```

- There is **no `@Transactional`** anywhere on `TeamResource` → `TeamService` → `TeamRepository` (consistent with the project's policy of avoiding `@Transactional` scope), so the read and the write run in **separate transactions** with a full HTTP handler's work in between.
- Even within one transaction, the plain `SELECT` takes **no locks** under MySQL's default REPEATABLE READ. Both concurrent transactions see "no conflict".
- **Multi-node removes even incidental serialization.** Two tutors, one tutor double-submitting, or a bulk team import retried across nodes all hit the same window.
- Both entry points are affected — `POST exercises/{id}/teams` (`TeamResource.java:178`) and `PUT exercises/{id}/teams/{teamId}` (`TeamResource.java:255`), both `@EnforceAtLeastTutor`. Updating a team's student set is a collection modification, which Hibernate performs as `DELETE` + `INSERT`, so an insert-time guard does cover the update path.

`StudentsAlreadyAssignedException` produces a good error message for the common case. It is **not** what keeps the data correct.

### Why a plain unique index cannot express the invariant

The rule is "at most one team per student **per exercise**", but `exercise_id` lives on `team`, not on `team_student`. The constraint spans two tables — which is exactly why the original authors reached for a trigger.

### Test results — what actually works

All four scenarios were run against MySQL 8.0 in a container, reproducing the production trigger and each candidate fix. "Race" is two concurrent transactions inserting the same student into two different teams of one exercise, with a 2.5 s overlap.

| Approach | Sequential conflict | Different exercise | **Concurrent race** | Verdict |
|---|---|---|---|---|
| Application check only | rejected | allowed | **both commit — violation** | Insufficient |
| Production trigger (plain `SELECT`) | rejected | allowed | **both commit — violation** | Insufficient |
| Trigger + `SELECT … FOR UPDATE` | — | — | — | **`ERROR 1442` — every insert fails** |
| **Denormalized column + `UNIQUE(exercise_id, student_id)`** | rejected (`ERROR 1062`) | allowed | **blocked, then rejected — 1 row survives** | **Correct** |

Two findings worth stating explicitly:

- **`FOR UPDATE` is not available here.** MySQL raises `ERROR 1442: Can't update table 'team_student' in stored function/trigger because it is already used by statement which invoked this stored function/trigger`. The trigger *creates* successfully and then fails on **every** insert — a change that would pass review and break team creation outright.
- **The trigger does not close the race.** Its `SELECT` is a non-locking snapshot read, so neither transaction sees the other's uncommitted row. Reproduced: both inserts committed, leaving student 700 in teams 10 and 11 of exercise 1. The trigger does still catch the *common* case (a student assigned in an earlier, committed transaction), which is why production data is clean — but it is not a guarantee.

### Recommended fix — denormalize `exercise_id`, enforce with a real unique index

The key insight that makes this cheap: **a `BEFORE INSERT` trigger may modify `NEW.*` on its own table** (that is not a prohibited self-update), so the denormalized column can be populated by the database. Hibernate keeps inserting only `(team_id, student_id)` and **the `@ManyToMany @JoinTable` mapping in `Team.java` needs no change at all** — no `TeamStudent` entity, no repository rework.

### Yes — this is expressible in Liquibase for both MySQL and PostgreSQL

Eight changesets. **Only the triggers are dialect-specific** (3 of 8); the schema changes are all portable Liquibase changes:

| # | Change | Dialect | Liquibase change type |
|---|---|---|---|
| 1 | add `exercise_id` column | **shared** | `<addColumn>` |
| 2 | backfill from `team` | **shared** | `<update>` + `valueComputed` correlated subquery |
| 3 | `NOT NULL` | **shared** | `<addNotNullConstraint>` |
| 4 | `UNIQUE(exercise_id, student_id)` | **shared** | `<addUniqueConstraint>` |
| 5 | populate trigger | MySQL | `<sql dbms="mysql">` |
| 6 | trigger function | PostgreSQL | `<sql dbms="postgresql">` |
| 7 | trigger | PostgreSQL | `<sql dbms="postgresql">` |
| 8 | drop legacy trigger | MySQL | `<sql dbms="mysql">` |

The backfill is the pleasant surprise: the correlated-subquery form works **identically** on both engines, so it needs no `dbms` split (MySQL's `UPDATE … JOIN … SET` and PostgreSQL's `UPDATE … SET … FROM` would each have needed their own changeset).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20260728120000-1-add-exercise-id-to-team-student" author="krusche">
        <preConditions onFail="MARK_RAN">
            <not><columnExists tableName="team_student" columnName="exercise_id"/></not>
        </preConditions>
        <addColumn tableName="team_student">
            <column name="exercise_id" type="bigint"/>
        </addColumn>
    </changeSet>

    <!-- Correlated subquery form works identically on MySQL and PostgreSQL. -->
    <changeSet id="20260728120000-2-backfill-exercise-id" author="krusche">
        <update tableName="team_student">
            <column name="exercise_id"
                    valueComputed="(SELECT t.exercise_id FROM team t WHERE t.id = team_student.team_id)"/>
            <where>exercise_id IS NULL</where>
        </update>
        <!-- UpdateDataChange has no automatic inverse; rolling back changeset -1 drops the column
             and with it the backfilled data, so an empty rollback is correct here. -->
        <rollback/>
    </changeSet>

    <changeSet id="20260728120000-3-exercise-id-not-null" author="krusche">
        <addNotNullConstraint tableName="team_student" columnName="exercise_id" columnDataType="bigint"/>
    </changeSet>

    <!-- The actual guarantee. Fails loudly if any installation already violates the invariant. -->
    <changeSet id="20260728120000-4-unique-exercise-student" author="krusche">
        <addUniqueConstraint tableName="team_student" columnNames="exercise_id, student_id"
                             constraintName="uk_team_student_exercise_student"/>
    </changeSet>

    <!-- Keep exercise_id populated for inserts issued by Hibernate, which only writes two columns.
         splitStatements=false is required: the body contains semicolons. Note there is deliberately
         no DELIMITER directive - that is a mysql client feature and is invalid over JDBC. -->
    <changeSet id="20260728120000-5-set-exercise-id-trigger-mysql" author="krusche" dbms="mysql">
        <sql splitStatements="false"><![CDATA[
CREATE TRIGGER team_student_set_exercise_id
BEFORE INSERT ON team_student
FOR EACH ROW
BEGIN
    IF NEW.exercise_id IS NULL THEN
        SET NEW.exercise_id = (SELECT t.exercise_id FROM team t WHERE t.id = NEW.team_id);
    END IF;
END
        ]]></sql>
        <rollback>
            <sql>DROP TRIGGER IF EXISTS team_student_set_exercise_id</sql>
        </rollback>
    </changeSet>

    <changeSet id="20260728120000-6-set-exercise-id-function-postgresql" author="krusche" dbms="postgresql">
        <sql splitStatements="false"><![CDATA[
CREATE OR REPLACE FUNCTION team_student_set_exercise_id() RETURNS TRIGGER AS $$
DECLARE
    v_exercise_id BIGINT;
BEGIN
    IF NEW.exercise_id IS NULL THEN
        SELECT t.exercise_id INTO v_exercise_id FROM team t WHERE t.id = NEW.team_id;
        NEW.exercise_id := v_exercise_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
        ]]></sql>
        <rollback>
            <sql>DROP FUNCTION IF EXISTS team_student_set_exercise_id()</sql>
        </rollback>
    </changeSet>

    <changeSet id="20260728120000-7-set-exercise-id-trigger-postgresql" author="krusche" dbms="postgresql">
        <sql splitStatements="false"><![CDATA[
CREATE TRIGGER team_student_set_exercise_id
BEFORE INSERT ON team_student
FOR EACH ROW EXECUTE FUNCTION team_student_set_exercise_id()
        ]]></sql>
        <rollback>
            <sql>DROP TRIGGER IF EXISTS team_student_set_exercise_id ON team_student</sql>
        </rollback>
    </changeSet>

    <!-- The legacy advisory trigger is now redundant: the unique index enforces the invariant
         properly. Only ever existed on long-lived MySQL installations. -->
    <changeSet id="20260728120000-8-drop-legacy-team-student-trigger" author="krusche" dbms="mysql">
        <sql>DROP TRIGGER IF EXISTS uc_team_student_exercise_id_and_student_id</sql>
        <!-- RawSQLChange has no automatic inverse. Restore the legacy trigger so a rollback really
             returns to the previous state. Note it is only advisory (a non-locking snapshot read),
             which is why the unique index above replaces it. -->
        <rollback>
            <sql splitStatements="false"><![CDATA[
CREATE TRIGGER uc_team_student_exercise_id_and_student_id
BEFORE INSERT ON team_student
FOR EACH ROW
BEGIN
    DECLARE v_exercise_id BIGINT;
    DECLARE v_conflict_team_id BIGINT;
    SELECT t.exercise_id INTO v_exercise_id FROM team t WHERE t.id = NEW.team_id;
    SELECT ts.team_id INTO v_conflict_team_id
    FROM team t JOIN team_student ts ON ts.team_id = t.id
    WHERE t.exercise_id = v_exercise_id AND ts.team_id <> NEW.team_id AND ts.student_id = NEW.student_id
    LIMIT 1;
    IF v_conflict_team_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Student is already part of another team for this exercise';
    END IF;
END
            ]]></sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

#### Verification — actually executed, not just reviewed

The changelog was appended to `master.xml` and the **whole** changelog run from an empty database on both engines: **MySQL 8.0.46** (utf8mb4 / utf8mb4_unicode_ci, matching production) and **PostgreSQL 17.10**. Liquibase 5.0.3.

| Check | MySQL | PostgreSQL |
|---|---|---|
| Full `master.xml` update from empty DB | ✅ 71 changesets | ✅ 72 changesets |
| Hibernate-style 2-column insert → trigger fills `exercise_id` | ✅ | ✅ |
| Same student, other team, **same** exercise | ✅ rejected (`ERROR 1062`) | ✅ rejected (`23505`) |
| Same student, **different** exercise | ✅ allowed | ✅ allowed |
| **Concurrent race** (2.5 s overlap, two transactions) | ✅ blocked → rejected, 1 row survives | ✅ blocked → rejected, 1 row survives |
| `rollback-count` of all new changesets | ✅ clean | ✅ clean |
| Re-apply after rollback | ✅ 6 changesets | ✅ 6 changesets |
| Re-run `update` is a no-op | ✅ 0 run / 71 previously run | ✅ 0 run / 72 previously run |

Resulting MySQL structure:

```sql
CREATE TABLE `team_student` (
  `team_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `exercise_id` bigint NOT NULL,
  PRIMARY KEY (`team_id`,`student_id`),
  UNIQUE KEY `uk_team_student_exercise_student` (`exercise_id`,`student_id`),
  KEY `fk_team_student_student_id` (`student_id`),
  CONSTRAINT `fk_team_student_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`) …,
  CONSTRAINT `fk_team_student_team_id` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) …
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

#### Liquibase gotchas found while testing

Each of these produced a failure before being fixed — worth knowing before writing similar changesets:

1. **Never put `DELIMITER $$` in a `<sql>` block.** `DELIMITER` is a `mysql` *client* directive, not SQL; over JDBC it is a syntax error. Use `splitStatements="false"` instead and omit it entirely.
2. **`splitStatements="false"` is mandatory for trigger and function bodies** — otherwise Liquibase splits on the internal semicolons and each fragment fails.
3. **`<update>` has no automatic inverse** (`No inverse to liquibase.change.core.UpdateDataChange created`). Add an explicit `<rollback/>`; here the empty form is correct because changeset 1's rollback drops the column.
4. **Raw `<sql>` has no automatic inverse** either — every `<sql>` changeset needs an explicit `<rollback>`.
5. **PostgreSQL needs the function and the trigger in separate changesets.** Dollar-quoted bodies plus a second statement in one block is fragile; splitting is reliable.
6. Unrelated but discovered en route: **rolling the whole stack back past this changelog fails** at the pre-existing `20260718160235-1` (`dropColumn` on `course`), which has no inverse. Not caused by this work, but it means "roll back the last N changesets" has a hard floor there.

#### One loose end: staleness if a team changes exercise

`exercise_id` is a copy, so it goes stale if `team.exercise_id` is ever reassigned. `TeamResource.updateTeam` calls `save(exercise, existingTeam)` with the exercise from the path variable, so a mismatch is possible in principle. Pick one:

- **Propagate** — `AFTER UPDATE ON team` trigger: `UPDATE team_student SET exercise_id = NEW.exercise_id WHERE team_id = NEW.id` when `OLD.exercise_id <> NEW.exercise_id`. Safe, but adds a second trigger per dialect.
- **Forbid** — raise if `team.exercise_id` changes while `team_student` rows exist. Teams moving between exercises is not a supported operation, so this is arguably the more honest constraint.

Recommended: **forbid**, plus an assertion in `updateTeam` that the path `exerciseId` matches the loaded team's exercise.

#### Fallback if the above is considered too invasive

Re-declare the **existing** production trigger verbatim in the changelog for both dialects (with `splitStatements="false"` and the `DECLARE`-vs-session-variable bug fixed — the current body declares three locals it never uses and assigns to `@session` variables instead, which leaks state across pooled connections).

This restores environment parity and defence-in-depth in ~30 lines per dialect, but **be explicit that it does not close the multi-node race** — do not ship it believing otherwise.

### Also do this in code

Add an integration test asserting the invariant is enforced **at the database level**: two inserts of one student into two teams of one exercise must fail. Without it, this protection will be deleted again by the next consolidation — that test is the real fix for the root cause.

Then catch the resulting constraint violation in `TeamResource` and translate it into the existing `StudentsAlreadyAssignedException` response, so the race loser gets the same clean 400 as the sequential case instead of a 500.

### Assessment

| | |
|---|---|
| **Duration** | **< 10 s total** on production. Step 1 is INSTANT (metadata only); step 2 backfills 198,586 rows in ~1–2 s; step 3 is INPLACE with concurrent DML on a 16 MB table, ~1–3 s; steps 4–5 are metadata only. |
| **Lock** | None blocking. `ADD UNIQUE KEY` is INPLACE and permits concurrent DML. |
| **Disk** | Negligible (16 MB table). |
| **Risk** | **Low–Medium.** Step 3 fails loudly if any violation exists — production has **0**, but re-verify immediately before deploying (§7). The behavioural change is that CI, dev and PostgreSQL start enforcing an invariant they previously did not: expect this to surface test fixtures that put one student in two teams of one exercise. Run the full suite plus `run-e2e-tests-local-multinode.sh`. |
| **Risk if dropped instead** | **The risk to avoid.** A cross-table invariant left to a check-then-act race on a multi-node cluster, reachable by any tutor. |
| **Risk if left undeclared** | Status quo: production has partial (non-race-safe) protection, every other environment has none, and the divergence is invisible to the changelog. |
| **Rollback** | `DROP INDEX uk_team_student_exercise_student ON team_student;` then drop the trigger and the column. Fully reversible — the column is additive and unmapped. |
| **Effort** | ~0.5 day for the migration + dual-dialect triggers, plus ~0.5 day for the integration test and the `TeamResource` error translation. |

---

## P2 — Fix temporal precision on six columns  ✅ **DONE on production**

**Priority: HIGH** (most likely to cause a real, hard-to-diagnose bug) · **Production DDL: complete**

> **Applied to production 2026-07-28 and verified.** All six columns are now `datetime(3)`, matching the changelogs exactly. Nullability survived the migration correctly — `science_event.timestamp` and `migration_changelog.date_executed` are still `NOT NULL`, which was the main thing to check since `modifyDataType` does not preserve nullability on all dialects.
>
> **Still outstanding:** the corresponding **Liquibase changesets**. Production is fixed, but the change is unrecorded in `DATABASECHANGELOG`, and **any other long-lived Artemis installation still has the old precision** (fresh installs are unaffected — `initial_schema.xml` already declares `datetime(3)`). Ship the changesets precondition-guarded so they `MARK_RAN` on this production instance and actually execute elsewhere. See §6 for the guard.
>
> This also settles the `datetime(6)` question below: production was aligned **down** to `(3)`, so the changelog needs no change and prod now matches code on all six columns.

### What

The changelogs declare `datetime(3)`; production has different precision. This predates the 9.0 consolidation — the pre-consolidation `initial_schema.xml` *also* said `datetime(3)`, so these columns were created by pre-2024 changelogs and never altered.

| # | Column | PROD (was) | CODE | PROD (now) | Rows | Size |
|---|---|---|---|---|---|---|
| **P2-a** | `science_event.timestamp` (NOT NULL) | `datetime` | `datetime(3)` | ✅ `datetime(3)` | 14,904,988 | 763 MB (0 MB idx) |
| **P2-b** | `llm_token_usage_trace.time` | `datetime` | `datetime(3)` | ✅ `datetime(3)` | 489,883 | 113 MB |
| **P2-c** | `file_upload.creation_date` | `datetime` | `datetime(3)` | ✅ `datetime(3)` | 3,189 | 2 MB |
| **P2-d** | `saved_post.completed_at` | `datetime` | `datetime(3)` | ✅ `datetime(3)` | 363 | < 1 MB |
| **P2-e** | `push_notification_device_configuration.expiration_date` | `datetime(6)` | `datetime(3)` | ✅ `datetime(3)` | 97 | < 1 MB |
| **P2-f** | `migration_changelog.date_executed` (NOT NULL) | `datetime(6)` | `datetime(3)` | ✅ `datetime(3)` | 0 | < 1 MB |

### Why it matters

`datetime` with precision 0 **silently rounds away milliseconds on write**. Every `ZonedDateTime` Hibernate persists to these columns loses sub-second precision *in production only*. Consequences:

- Equality comparisons on these timestamps behave differently in production than in any test.
- Ordering by them is unstable when many rows land in the same second — `science_event` writes 15 M rows, so same-second collisions are the norm, not the exception.
- This is exactly the failure mode the project's own testing convention guards against (`CLAUDE.md`: compare `ZonedDateTime` via `toInstant()`).

The two `datetime(6)` cases are the opposite direction — production is *more* precise than declared. See the decision note below.

### SQL

```sql
-- P2-a  MAINTENANCE WINDOW (see §6) — 763 MB rebuild, writes blocked
ALTER TABLE science_event
  MODIFY COLUMN `timestamp` datetime(3) NOT NULL;

-- P2-b
ALTER TABLE llm_token_usage_trace
  MODIFY COLUMN `time` datetime(3) NULL;

-- P2-c
ALTER TABLE file_upload
  MODIFY COLUMN creation_date datetime(3) NULL;

-- P2-d
ALTER TABLE saved_post
  MODIFY COLUMN completed_at datetime(3) NULL;

-- P2-e / P2-f — see decision note; if aligning DOWN to the changelog:
ALTER TABLE push_notification_device_configuration
  MODIFY COLUMN expiration_date datetime(3) NULL;
ALTER TABLE migration_changelog
  MODIFY COLUMN date_executed datetime(3) NOT NULL;
```

Liquibase equivalent: `<modifyDataType>` (plus `<addNotNullConstraint>` where the column is `NOT NULL`, because `modifyDataType` does not preserve nullability on all dialects — verify the generated SQL with `updateSQL` before shipping).

### Assessment

| Item | Algorithm | Duration (est.) | Lock |
|---|---|---|---|
| **P2-a** `science_event` | COPY | **2–6 min** | writes blocked |
| **P2-b** `llm_token_usage_trace` | COPY | 10–30 s | writes blocked |
| **P2-c** `file_upload` | COPY | < 1 s | negligible |
| **P2-d** `saved_post` | COPY | < 1 s | negligible |
| **P2-e** `push_notification…` | COPY | < 1 s | negligible |
| **P2-f** `migration_changelog` | COPY | < 1 s | 0 rows |
| **Subtotal** | | **~2.5–7 min**, of which ~2–6 min is write-blocked on one table | |

Disk headroom: P2-a needs ~763 MB free (35 GB available) — comfortable.

| | |
|---|---|
| **Risk** | **Low–Medium.** Widening `datetime`→`datetime(3)` cannot lose data. The risk is *availability*, not correctness: P2-a blocks writes to `science_event` for several minutes. Because this table is append-only telemetry, blocked writes will surface as request-latency spikes on any endpoint that records a science event. |
| **Risk if skipped** | Millisecond truncation persists in production only. Low blast radius per event, but permanently divergent from every test environment. |
| **Rollback** | Reverse `MODIFY COLUMN` back to `datetime`. **Truncated historical values are not recoverable** — but no data is *destroyed* by the forward migration. |
| **Decision needed** | **P2-e / P2-f are `datetime(6)` in production, i.e. more precise than the changelog.** Migrating *down* to `(3)` discards precision (harmless for 97 rows and 0 rows respectively). Cleaner alternative: change the **changelog** to `datetime(6)` for these two and leave production alone. Recommended: align the changelog up, not production down. |

---

## P3 — Drop three redundant indexes  ✅ **DONE on production**

**Priority: MEDIUM** (pure hygiene, but free) · **Production DDL: complete**

> **Applied to production 2026-07-28 and verified.** All three redundant indexes are gone and — the important check — **every covering index survived**: `jhi_user.login` (UNIQUE), `competency_user.PRIMARY (competency_id, user_id)`, and `participation_vcs_access_token.user-participation_unique (user_id, participation_id)` are all still in place. Dropping the covering index instead of the duplicate was the one way this could have gone wrong, and it didn't.
>
> **Convergence achieved:** `information_schema.STATISTICS` for all three tables now matches a fresh Liquibase install exactly — zero differences.
>
> **Still outstanding:** the **Liquibase changesets**. As with P2, production is fixed but unrecorded, and other long-lived Artemis installations still carry all three indexes. Ship them `<preConditions onFail="MARK_RAN"><indexExists …/></preConditions>`-guarded so they no-op here and on fresh installs, and execute elsewhere.
>
> **Note on space:** `INDEX_LENGTH` is unchanged (188 / 13 / 9 MB). `DROP INDEX` frees pages for reuse *within* the tablespace but does not shrink the `.ibd` file, and the `information_schema` size statistics are not refreshed immediately. The real benefit was never disk — it is reduced write amplification on every `INSERT`/`UPDATE`/`DELETE`, which matters most on `participation_vcs_access_token` (732 K rows, written on every repository access).

### What

Three indexes exist in production that no current changeset creates. All three trace to changelogs deleted during consolidation, and all three are **exact duplicates** of another index on the same table.

| Index | Table (size) | Duplicate of | Redundant? |
|---|---|---|---|
| `idx_user_login` (UNIQUE `login`) | `jhi_user` (34,109 rows / 14 MB) | unique index `login` on the same column | Fully |
| `uc_learning_goal_user` (UNIQUE `competency_id,user_id`) | `competency_user` (107,357 rows / 21 MB) | the **PRIMARY KEY**, same columns, same order | Fully |
| `idx_participation_vcs_access_token_user_participation` (`user_id,participation_id`) | `participation_vcs_access_token` (732,044 rows / 259 MB) | unique index `user-participation_unique`, same columns, same order | Fully |

### Why

Each one costs write amplification and buffer-pool space for zero read benefit — the optimizer can never prefer them over the covering duplicate. `participation_vcs_access_token` is the worst offender: 188 MB of its 259 MB is index, on a table that takes a write on every repository access.

### SQL

```sql
DROP INDEX idx_user_login ON jhi_user;
DROP INDEX uc_learning_goal_user ON competency_user;
DROP INDEX idx_participation_vcs_access_token_user_participation
  ON participation_vcs_access_token;
```

Each wrapped in a changeset with a guard so fresh installs skip it:

```xml
<changeSet id="…-drop-redundant-idx-user-login" author="…">
    <preConditions onFail="MARK_RAN">
        <indexExists tableName="jhi_user" indexName="idx_user_login"/>
    </preConditions>
    <dropIndex tableName="jhi_user" indexName="idx_user_login"/>
</changeSet>
```

### Assessment

| | |
|---|---|
| **Duration** | **< 5 s total.** `DROP INDEX` is INPLACE, no table rebuild, size-independent. |
| **Lock** | None — concurrent DML permitted throughout. |
| **Risk** | **Very low.** Each dropped index is provably covered by another index with identical leading columns. No query plan can regress. |
| **Verification before** | `SHOW INDEX FROM <table>` to confirm the duplicate is still present. |
| **Rollback** | Re-create the index. On `participation_vcs_access_token` re-creation costs ~10–30 s (INPLACE, non-blocking). |

---

## P4 — Normalize four unique-constraint names

**Priority: MEDIUM** (prevents a future changeset from failing)

### What

Production carries JHipster-era explicit constraint names; fresh installations get MySQL's auto-generated name (the column name), because the consolidated changelog declares the uniqueness without naming it.

| Table (size) | PROD name | CODE name |
|---|---|---|
| `course` (473 rows / 2 MB) | `UC_COURSE_SHORT_NAME` | `short_name` |
| `exercise` (9,369 rows / 69 MB) | `UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL` | `quiz_point_statistic_id` |
| `exercise` (9,369 rows / 69 MB) | `UC_EXERCISESOLUTION_PARTICIPATION_ID_COL` | `solution_participation_id` |
| `tutorial_group` (472 rows / < 1 MB) | `tutorial_group_channel_uq` | `tutorial_group_channel_id` |

### Why

Cosmetic **today**, but a live trap: the moment anyone writes `<dropUniqueConstraint constraintName="short_name"/>` or `constraintName="UC_COURSE_SHORT_NAME"`, it succeeds in one environment and fails in the other. The consolidation created a name fork that has to be closed before it bites.

### ⚠️ Incident 2026-07-28 — the first attempt partly failed

**My original SQL for this section was wrong: it ordered `DROP` before `ADD`.** Three of those indexes are the only index backing a foreign key, so MySQL refused the drop:

```
Cannot drop index 'UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL': needed in a foreign key constraint
Cannot drop index 'UC_EXERCISESOLUTION_PARTICIPATION_ID_COL': needed in a foreign key constraint
Cannot drop index 'tutorial_group_channel_uq': needed in a foreign key constraint
```

The subsequent `ADD`s succeeded, leaving **duplicate unique indexes** on three columns. (I flagged this exact FK-index hazard under P5 but failed to apply it to P4.)

| Table | Column | State after the partial run |
|---|---|---|
| `course` | `short_name` | ✅ **complete** — only `uk_course_short_name` (no FK on this column, so the drop succeeded) |
| `exercise` | `quiz_point_statistic_id` | ⚠️ both `UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL` **and** `uk_exercise_quiz_point_statistic` |
| `exercise` | `solution_participation_id` | ⚠️ both `UC_EXERCISESOLUTION_PARTICIPATION_ID_COL` **and** `uk_exercise_solution_participation` |
| `tutorial_group` | `tutorial_group_channel_id` | ⚠️ both `tutorial_group_channel_uq` **and** `uk_tutorial_group_channel` |

### Repair SQL — verified

Because a second unique index on the same column now exists, it can serve the foreign key, so the legacy index becomes droppable. Three statements:

```sql
ALTER TABLE exercise
    DROP INDEX UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL,
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE exercise
    DROP INDEX UC_EXERCISESOLUTION_PARTICIPATION_ID_COL,
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE tutorial_group
    DROP INDEX tutorial_group_channel_uq,
    ALGORITHM=INPLACE, LOCK=NONE;
```

The explicit `ALGORITHM=INPLACE, LOCK=NONE` is a safety assertion, not an optimisation: if MySQL could not perform the change online it would **refuse the statement** rather than silently fall back to a locking table copy. Verified accepted on 8.0.46.

**Verified** by reproducing production's exact post-failure state on MySQL 8.0.46 running the real Artemis schema (`master.xml`), then applying the repair:

| Check | Result |
|---|---|
| All three `DROP INDEX` statements | ✅ succeed |
| Remaining indexes | ✅ exactly one UNIQUE per column, the `uk_*` name |
| Foreign keys still present | ✅ all three intact |
| Foreign key still **enforced** | ✅ `ERROR 1452` on a dangling reference |
| Unique constraint still **enforced** | ✅ `ERROR 1062` on a duplicate (both `exercise` and `tutorial_group`) |
| `ALGORITHM=INPLACE, LOCK=NONE` accepted | ✅ no fallback to a locking copy |
| Wall clock | ✅ 0.1 s for all three |

`DROP INDEX` is INPLACE with concurrent DML — **under 5 seconds total, no lock, no rebuild** (`exercise` is 69 MB, `tutorial_group` < 1 MB).

### The correct recipe (for any future rename)

Use `RENAME INDEX` — **verified to work on an FK-backing index**, and it is atomic, so there is no window where uniqueness is unenforced and no duplicate-index intermediate state:

```sql
ALTER TABLE exercise RENAME INDEX UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL
                            TO uk_exercise_quiz_point_statistic;
```

Failing that, always **ADD before DROP** — never the reverse — on any column that backs a foreign key.

### Changelog side — the repair alone does not achieve convergence

After the repair, production has four explicit `uk_*` names while a **fresh install still auto-names these indexes after their column** (`short_name`, `quiz_point_statistic_id`, `solution_participation_id`, `tutorial_group_channel_id`). The name fork has moved, not closed. Declare the names in the changelog to finish P4:

```xml
<addUniqueConstraint tableName="course" columnNames="short_name"
                     constraintName="uk_course_short_name"/>
<addUniqueConstraint tableName="exercise" columnNames="quiz_point_statistic_id"
                     constraintName="uk_exercise_quiz_point_statistic"/>
<addUniqueConstraint tableName="exercise" columnNames="solution_participation_id"
                     constraintName="uk_exercise_solution_participation"/>
<addUniqueConstraint tableName="tutorial_group" columnNames="tutorial_group_channel_id"
                     constraintName="uk_tutorial_group_channel"/>
```

Guard each with a `columnNames`-based `<indexExists>` precondition so it `MARK_RAN`s on production and executes elsewhere.

**Note on naming consistency.** The changelog already declares two *other* unique indexes on `exercise` with legacy names — `UC_EXERCISETEMPLATE_PARTICIPATION_ID_COL` and `UC_EXERCISEUBMISSION_POLICY_ID_COL` (`00000000000000_initial_schema.xml:2856,2859`) — and those match production, so they are **not** drift. After this repair `exercise` carries a mix of `UC_*` and `uk_*` names. Optionally rename those two to `uk_*` as well (via `RENAME INDEX`, cheap and safe) for intra-table consistency — note the legacy name contains a typo, `UC_EXERCISEUBMISSION` is missing the `S` in "SUBMISSION", which is a further argument for retiring the JHipster-era names.

### Assessment

| | |
|---|---|
| **Duration** | **< 10 s total.** Both `DROP INDEX` and `ADD UNIQUE INDEX` are INPLACE with concurrent DML. `exercise` is the largest at 69 MB → a few seconds. |
| **Lock** | None. |
| **Risk** | **Low–Medium.** There is a sub-second window between `DROP` and `ADD` during which uniqueness is unenforced. On `course` (473 rows) and `exercise` (9 K rows) with low write rates, a concurrent violating insert is very unlikely — but run each pair inside a single changeset and, if you want to eliminate the window entirely, add the new constraint first under a temporary name, then drop the old one, then rename. |
| **Rollback** | Reverse the pair. |

---

## P5 — (Optional) Align composite primary-key column order

**Priority: LOW**

### What

Four join tables have their composite PK columns in a different order in production than in a fresh install:

| Table | PROD PK | CODE PK | Rows | Size |
|---|---|---|---|---|
| `course_organization` | `(organization_id, course_id)` | `(course_id, organization_id)` | **0** | < 1 MB |
| `user_organization` | `(organization_id, user_id)` | `(user_id, organization_id)` | **0** | < 1 MB |
| `programming_exercise_task_test_case` | `(test_case_id, task_id)` | `(task_id, test_case_id)` | 84,082 | 13 MB |
| `push_notification_device_configuration` | `(device_type, user_id, token)` | `(device_type, token, user_id)` | 97 | < 1 MB |

Consequence: different index prefixes are usable, so query plans for these tables differ between production and every test environment. Fresh installs additionally carry FK indexes (`FKdythnvneadrsbvfa3hxd1tq4h`, `FKfdnaj8emi62iffmg6w6ykjxf4`) that production does not need, because production's PK prefix already covers the FK column.

### Why it is optional

Two of the four tables are **empty** in production and the other two are tiny, so alignment is cheap. But the benefit is only "environments match" — no current query is known to regress. Weigh against the fact that changing a PK is the most invasive operation in this document.

### SQL (note the FK-index ordering constraint)

`DROP PRIMARY KEY` is not safe to issue naively: these tables have outgoing foreign keys, and InnoDB requires an index on every FK column. Dropping the PK can remove the only index backing an FK. Safe sequence per table:

```sql
-- example: course_organization  (0 rows — trivial, shown for the pattern)
ALTER TABLE course_organization
  ADD INDEX tmp_fk_course (course_id),
  ADD INDEX tmp_fk_org (organization_id);
ALTER TABLE course_organization
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (course_id, organization_id);
ALTER TABLE course_organization DROP INDEX tmp_fk_course;   -- now covered by PK prefix
-- keep tmp_fk_org, renamed, to match the fresh-install FK index
```

### Assessment

| | |
|---|---|
| **Duration** | **< 15 s total** (two empty tables, one 13 MB, one 97 rows). `programming_exercise_task_test_case` dominates at a few seconds. |
| **Lock** | `DROP/ADD PRIMARY KEY` is **COPY** — writes blocked for the duration. Negligible here given the sizes. |
| **Risk** | **Medium** — disproportionate to the benefit. PK changes touch FK-backing indexes; a mis-ordered statement can leave an FK without an index or fail mid-way. |
| **Recommendation** | **Defer.** If you want convergence, the cheaper direction is to change the **changelog** to match production's order (zero production risk, zero downtime). Only do the production-side ALTER if a query plan problem is actually measured. |

---

## P6 — Resolve the `utf8mb4_bin` collation fork  ✅ **DONE on production**

**Priority: LOW** · **Production DDL: complete**

> **Applied to production 2026-07-28 via Option A** (aligned production to the changelog / server default). Both columns are now `utf8mb4_unicode_ci`, and the **whole schema is now uniformly `utf8mb4_unicode_ci` — 384 of 384 character columns, no exceptions anywhere.** That is a better outcome than the Option B I had leaned toward, because it removes a special case rather than documenting one.
>
> **No changeset is required for these two columns.** Unlike P2 and P3, production now simply matches what a standard Artemis install produces, and fresh installs were never affected.
>
> **One residual gap worth closing separately.** The collation is still not *declared* anywhere in the changelog — it comes from the server default, which Artemis sets in `docker/mysql.yml:20` (`--character_set_server=utf8mb4 --collation-server=utf8mb4_unicode_ci`). So:
> - Deployments using the shipped Docker compose → correct.
> - Deployments against an **externally managed** MySQL 8 (RDS, Cloud SQL, a distro default) → a fresh install gets **`utf8mb4_0900_ai_ci`**, MySQL 8's out-of-the-box default. This is not hypothetical: my first reference build produced exactly that before I forced the collation to match production.
> - The requirement is documented **nowhere** outside that compose file — a grep of `documentation/` finds nothing.
>
> **Recommended follow-up (docs, not DDL):** state the required charset/collation in the database setup documentation, and have the C8 drift check assert the *server* default rather than compare column collations against a hardcoded value — otherwise it will report false positives on any correctly-configured non-Docker deployment.

### What

Two columns are `utf8mb4_bin` in production but `utf8mb4_unicode_ci` on fresh installs:

| Column | Rows | Size |
|---|---|---|
| `short_answer_solution.text` `varchar(255)` | 1,101 | < 1 MB |
| `short_answer_submitted_text.text` `varchar(255)` | 124,312 | 19 MB |

`COLLATE` appears **nowhere** in the liquibase directory's git history, so this was set outside Liquibase at some point.

### Why the impact is limited

Short-answer grading is done entirely in Java — `ShortAnswerSubmittedText.isSubmittedTextCorrect()` uses `FuzzySearch.ratio()` with an explicit `toLowerCase()` unless `matchLetterCase` is set. **The collation has no effect on grading.** It only affects SQL-level comparison and sorting of these columns, which no repository query performs.

### SQL

```sql
-- Option A: align production to the changelog (case-insensitive)
ALTER TABLE short_answer_solution
  MODIFY COLUMN text varchar(255) COLLATE utf8mb4_unicode_ci NULL;
ALTER TABLE short_answer_submitted_text
  MODIFY COLUMN text varchar(255) COLLATE utf8mb4_unicode_ci NULL;

-- Option B (preferred): leave production alone and declare the binary
--   collation in the changelog so all environments match production.
```

### Assessment

| | |
|---|---|
| **Duration** | **< 5 s** (COPY rebuild of 19 MB + < 1 MB). |
| **Lock** | Writes blocked, but only for seconds. |
| **Risk** | **Low.** No functional dependency on the collation. |
| **Recommendation** | **Option B.** Do not touch production. Declare the intended collation explicitly in the changelog — the actual defect here is that it is *undeclared*, not that it differs. Whichever you choose, make it explicit so it stops drifting. |

---

## P7 — Drop dead tables (needs a data-retention decision first)

**Priority: LOW — blocked on a product decision**

### What

Four tables hold live production data but are referenced by **no entity and no Java code** anywhere in the repository:

| Table | Rows | Size | Why it is dead |
|---|---|---|---|
| `competency_learning_path` | 130,393 | 6 MB | `LearningPath.competencies` is `@Transient` |
| `testwise_coverage_report_entry` | 6,591 | 1 MB | Testwise coverage feature removed |
| `coverage_file_report` | 437 | < 1 MB | Testwise coverage feature removed |
| `coverage_report` | 187 | < 1 MB | Testwise coverage feature removed |

The changelogs still create all four (`00000000000000_initial_schema.xml:497, 2208, …`), so every fresh install gets them too.

### SQL (after the retention decision)

```sql
-- FK order matters: children first
DROP TABLE testwise_coverage_report_entry;
DROP TABLE coverage_file_report;
DROP TABLE coverage_report;
DROP TABLE competency_learning_path;
```

### Assessment

| | |
|---|---|
| **Duration** | **< 2 s total.** |
| **Lock** | Brief metadata lock. |
| **Risk** | **Low technically, but irreversible.** 137 K rows of historical data are deleted. |
| **Blocking question** | Is `competency_learning_path` (130 K rows) needed for any historical learning-analytics reporting? If yes, archive before dropping. |
| **Recommendation** | Take a targeted dump of the four tables, then drop them in the same changeset that removes their `createTable` blocks from the changelog. |

---

## P8 — Accepted divergence: do NOT normalize boolean storage

**Priority: NONE — documented decision**

### What

86 boolean columns are `bit(1)` in production; a fresh install creates them as `tinyint` (Liquibase 5 renders `boolean` as `tinyint`, whereas Hibernate's `MySQLDialect` prefers `bit`). Production is itself mixed: 86 `bit(1)` + 43 `tinyint` (the newer ones), across 38 tables.

### Why this must stay as-is

Normalizing means a **`COPY` rebuild of the largest tables in the database**:

| Table | Size | `bit(1)` columns |
|---|---|---|
| `feedback` | **8,507 MB** | 2 |
| `submission` | **4,479 MB** | 3 |
| `result` | **3,928 MB** | 4 |
| `participation` | **1,381 MB** | 1 |
| `exercise` | 69 MB | 8 |
| …33 more tables | | |

That is **≥ 18 GB of write-blocked rebuilds** — hours of downtime, and `feedback` alone needs 8.5 GB of the 35 GB free disk. Both storage types work identically through Connector/J and Hibernate.

### Assessment

| | |
|---|---|
| **Estimated duration if attempted** | **3–8 hours**, fully write-blocked on the four largest tables |
| **Benefit** | Cosmetic consistency only |
| **Decision** | **Do not do this.** Record it as accepted divergence. |
| **Actionable takeaway** | Never write a changeset or native query that assumes either storage type for a boolean column. |

Likewise, **132 column-ordinal-position differences** are cosmetic (Hibernate always names columns explicitly) — no action.

---

# PART 2 — Code fixes

These are changes to the repository. Several of them are the *correct* fix for a finding whose production-side alternative would be wrong or expensive.

---

## C1 — Add the missing `result_rating` unique constraint to the changelog

**Priority: HIGH** · **Production needs no change**

`Rating.result` is `@OneToOne` (`src/main/java/de/tum/cit/aet/artemis/assessment/domain/Rating.java:24`). Production **has** the unique index (named `result_id`); the consolidated changelog **lost** it, so fresh installs and all test environments do not enforce it.

**Production is correct here — the changelog is wrong.**

Verified safe: 10,818 rows / 10,818 distinct `result_id`.

```xml
<changeSet id="…-result-rating-unique" author="…">
    <preConditions onFail="MARK_RAN">
        <not><indexExists tableName="result_rating" columnNames="result_id"/></not>
    </preConditions>
    <addUniqueConstraint tableName="result_rating" columnNames="result_id"
                         constraintName="uk_result_rating_result_id"/>
</changeSet>
```

The `columnNames`-based precondition is essential — production already has this index under a different name, so a name-based check would create a duplicate.

| | |
|---|---|
| **Production duration** | 0 s (precondition marks it as run) |
| **Fresh-install duration** | < 1 s |
| **Risk** | Very low |
| **Effort** | 15 min |

---

## C2 — Fix six mis-annotated `@OneToOne` associations → `@ManyToOne`

**Priority: HIGH** · **Production needs no change — do NOT add these constraints**

### What the data proves

Hibernate's model implies a unique constraint for every `@OneToOne` owning side. I tested all 29 such implied constraints against real production data. Six are **factually false** — the relationship is many-to-one, and the `@OneToOne` annotation is simply wrong:

| Association | File | Duplicate FK groups in prod | Correct mapping |
|---|---|---|---|
| `PlagiarismSubmission.plagiarismComparison` | `plagiarism/domain/PlagiarismSubmission.java:71` | **5,543** | `@ManyToOne` |
| `ShortAnswerSubmittedText.spot` | `quiz/domain/ShortAnswerSubmittedText.java:38` | **558** | `@ManyToOne` |
| `AssessmentNote.creator` | `assessment/domain/AssessmentNote.java:31` | **148** | `@ManyToOne` |
| `BuildJob.result` | `localci/domain/BuildJob.java:42` | **106** | `@ManyToOne`, drop `unique = true` |
| `CourseLearnerProfile.course` | `atlas/domain/profile/CourseLearnerProfile.java:39` | **31** | `@ManyToOne` |
| `Participation` `@UniqueConstraint(student_id, exercise_id, initialization_state)` | `exercise/domain/participation/Participation.java:46` | **76,328** | remove **this one only** |

These are semantically obvious once stated: one user creates *many* assessment notes; one result has *many* build jobs (retries); many students answer the *same* short-answer spot; one course has *many* learner profiles (one per student).

Two of these deserve specific attention:

- **`BuildJob.result` is not merely an implied constraint** — it is declared explicitly as `@OneToOne(fetch = FetchType.LAZY) @JoinColumn(unique = true)`. Someone deliberately asked for uniqueness that production data contradicts 106 times over. Remove `unique = true` along with the `@OneToOne`.
- **`Participation` declares *two* unique constraints** (`Participation.java:46-47`). Only the `student_id` variant is violated. The team variant — `(team_id, exercise_id, initialization_state)` — was checked against production and has **0 duplicate groups**, so it is a genuine invariant. Remove only the student-based declaration; the team-based one is a candidate for **C3** (enforce it).

### Why nothing is broken today

All six are **owning-side only** — there is no `@OneToOne(mappedBy = …)` inverse anywhere in the codebase. Hibernate therefore never expects "at most one row" at runtime, so duplicates are harmless. The bug is latent, not active.

### Why it still must be fixed

1. **It documents the model incorrectly**, which is how someone eventually adds an inverse side and gets `NonUniqueResultException` in production only.
2. **It makes the schema unverifiable.** Any attempt to run Hibernate schema validation, `ddl-auto=update`, or a Liquibase-Hibernate diff produces 29 phantom "missing constraint" findings, six of which would **break production if implemented**. That noise is exactly why this drift went unnoticed.

### Fix

Change `@OneToOne` → `@ManyToOne` on the five associations, and delete the false `@UniqueConstraint` from `Participation`. No schema change, no migration.

| | |
|---|---|
| **Production duration** | 0 s — no DDL |
| **Risk** | **Low.** `@ManyToOne` is strictly more permissive than `@OneToOne` on the owning side; fetch semantics are unchanged (both default to `EAGER` unless specified). Verify no code relies on `@OneToOne` cascade defaults. |
| **Effort** | 1–2 h including a test run |

---

## C3 — Add the 16 unique constraints that *do* hold

**Priority: MEDIUM**

The other side of the same investigation: 16 implied constraints are satisfied by production data (**0 duplicate groups**) and represent genuine invariants worth enforcing.

| Table | Column(s) | Table size | Duplicates in prod |
|---|---|---|---|
| `attachment` | `attachment_unit_id` | 2 MB | 0 |
| `conversation` | `exercise_id` | — | 0 |
| `conversation` | `lecture_id` | — | 0 |
| `conversation` | `exam_id` | — | 0 |
| `course` | `online_course_configuration_id` | 2 MB | 0 |
| `course` | `tutorial_groups_configuration_id` | 2 MB | 0 |
| `exercise` | `plagiarism_detection_config_id` | 69 MB | 0 |
| `exercise` | `team_assignment_config_id` | 69 MB | 0 |
| `grading_scale` | `course_id` | < 1 MB | 0 |
| `grading_scale` | `exam_id` | < 1 MB | 0 |
| `jhi_user` | `learner_profile_id` | 14 MB | 0 |
| `post` | `plagiarism_case_id` | — | 0 |
| `programming_exercise_details` | `programming_exercise_build_config_id` | 2 MB | 0 |
| `text_block` | `feedback_id` | — | 0 |
| `tutorial_group_schedule` | `tutorial_group_id` | < 1 MB | 0 |
| `participation` | `team_id, exercise_id, initialization_state` | 1,381 MB | 0 |

Two more are **borderline** and need a look at the data before deciding:

- `participant_score.last_result_id` — 3 duplicate groups
- `participant_score.last_rated_result_id` — 2 duplicate groups

Such small counts on a table maintained by concurrent updates suggest **data corruption from a race**, not a legitimately non-unique relationship. Investigate those 5 groups before either enforcing or abandoning the constraint. Also `exercise_categories(exercise_id, categories)` has exactly 1 duplicate group — an element-collection duplicate that is almost certainly noise worth cleaning.

| | |
|---|---|
| **Production duration** | **~1–2 min total.** `ADD UNIQUE INDEX` is INPLACE with concurrent DML throughout. `participation` (1,381 MB / 2.5 M rows) dominates at ~30–90 s; `exercise` (69 MB) takes a few seconds; the remaining 14 are sub-second. |
| **Lock** | None. |
| **Risk** | **Medium** — the constraints are verified against *today's* data. If a race can create duplicates, the changeset will fail at deploy time and block startup. Re-run the duplicate check immediately before deploying, and ship these in their own changeset (not bundled with P1/P2) so a failure has a narrow blast radius. |
| **Benefit** | Turns six classes of silent data corruption into an immediate constraint violation. |
| **Effort** | 2–3 h (changesets + re-verification + the `participant_score` investigation) |

---

## C4 — Add `NOT NULL` where the entity already declares it

**Priority: MEDIUM**

26 columns are annotated `nullable = false` but are NULL-able in the database (in **both** production and fresh installs — this is not drift, it is a long-standing code/schema inconsistency). Neither layer enforces them.

### 22 columns: verified zero NULLs in production — safe to enforce

`competency_relation.head_competency_id`, `competency_relation.tail_competency_id`, `course.max_team_complaints`, `data_export.created_by`, `exam_session.created_by`, `exam.visible_date`, `exam.start_date`, `exam.end_date`, `faq.created_by`, `file_upload.creation_date`, `forwarded_message.source_type`, `iris_message.session_id`, `iris_session.user_id`, `jhi_user.created_by`, `plagiarism_comparison_matches.plagiarism_comparison_id`, `plagiarism_result_similarity_distribution.idx`, `plagiarism_result_similarity_distribution.plagiarism_result_id`, `programming_exercise_details.project_key`, `saved_post.post_type`, `saved_post.status`, `user_groups.user_groups`

Verified examples: `Exam.visibleDate/startDate/endDate` (`exam/domain/Exam.java:60,66,72`), `SavedPost.status/postType` (`communication/domain/SavedPost.java:28,32`), `AbstractAuditingEntity.createdBy` (`core/domain/AbstractAuditingEntity.java:27`).

```sql
-- representative; largest tables shown
ALTER TABLE iris_session   MODIFY COLUMN user_id    bigint NOT NULL;  -- 251 MB
ALTER TABLE iris_message   MODIFY COLUMN session_id bigint NOT NULL;  -- 144 MB
ALTER TABLE exam_session   MODIFY COLUMN created_by varchar(50) NOT NULL;  -- 39 MB
ALTER TABLE user_groups    MODIFY COLUMN user_groups varchar(255) NOT NULL; -- 31 MB
-- …18 more, all < 15 MB
```

### 4 columns: NULLs present — need a backfill decision first

| Column | NULLs | Total rows | Table size |
|---|---|---|---|
| `exam_user.created_by` | **39,237** | 104,689 | 21 MB |
| `complaint_response.created_by` | **3,842** | 26,963 | 14 MB |
| `student_exam.created_by` | **3,370** | 107,707 | 35 MB |
| `plagiarism_submission_element.length` | **204,049** | 10,597,408 | 1,229 MB |
| `plagiarism_submission_element.line` | **204,049** | 10,597,408 | 1,229 MB |

For the three `created_by` columns the pragmatic fix is a backfill to `'system'` (matching the e2e seed convention) before adding the constraint. For `plagiarism_submission_element` there is no sensible default for `length`/`line` — **recommend removing `nullable = false` from the annotation instead**, since the data says these are genuinely optional.

### Assessment

| | |
|---|---|
| **Duration (22 safe columns)** | **~30–90 s total.** `MODIFY … NOT NULL` is INPLACE, rebuilds the table, but **permits concurrent DML**. `iris_session` (251 MB) and `iris_message` (144 MB) dominate at ~15–40 s each. |
| **Duration (backfills)** | `UPDATE` of 39 K + 3.8 K + 3.4 K rows → **< 10 s total**. Batch them to keep transactions small. |
| **Lock** | None (INPLACE with concurrent DML). |
| **Risk** | **Medium.** Requires strict `sql_mode` (production has `STRICT_TRANS_TABLES` — confirmed). The real risk is that a code path currently writes NULL and will start failing. Grep for writes to each column before enforcing; `created_by` is populated by Spring Data auditing, which is only active when an `AuditorAware` is in scope — verify batch/migration paths set it. |
| **Effort** | 3–4 h (verification per column + changesets + backfills) |

---

## C5 — Drop dead schema: 14 columns and 4 tables  → **PR [#13334](https://github.com/ls1intum/Artemis/pull/13334)**

**Priority: MEDIUM** · verified 2026-07-28 by a full sweep · **changeset written, dual-engine tested, PR open**

> The Iris `legacy_*` columns are **included** — the owner approved completing the staged deletion, and the data was verified as carried forward into `entity_id` (359,947/359,948 and 75,345/75,346 matching; the 2 exceptions are recorded verbatim in the changelog comment).

Derived authoritatively by diffing the Hibernate mapping model (all 244 annotated classes) against the schema Liquibase builds, then verifying every candidate by grepping the **entire** repository — Java, client TypeScript, tests, generated OpenAPI, e2e — not just the server.

**The codebase already asks for three of these.** They are not speculative cleanups:

| Marker | Location |
|---|---|
| `// TODO: drop the legacy "feedbacks_order" DB column in a follow-up PR via a Liquibase changeset.` | `assessment/domain/Result.java:105` |
| `// TODO: delete publish_build_plan_url from exercise using liquibase` | `programming/domain/ProgrammingExercise.java:60` |
| `// The legacy correct_mappings_order column ... is now orphaned; tracked in #12807 for a follow-up Liquibase changeset.` | `quiz/domain/DragAndDropQuestion.java:78`, `quiz/domain/ShortAnswerQuestion.java:53` |

### Columns — 12 safe to drop now

Zero references anywhere outside the changelogs and the TODO comments above.

| Column | Origin | Notes |
|---|---|---|
| `feedback.feedbacks_order` | #12610 stopped ordering `result.feedbacks` | TODO at `Result.java:105`. On a 42.8 M-row / 8.5 GB table — but `DROP COLUMN` is INSTANT, so no rebuild |
| `drag_and_drop_mapping.correct_mappings_order` | #12640 | tracked in **#12807** |
| `short_answer_mapping.correct_mappings_order` | #12640 | tracked in **#12807** |
| `exercise.publish_build_plan_url` | Bamboo-era | TODO at `ProgrammingExercise.java:60` |
| `exercise.allowed_per_tutor_batch_count` | legacy | |
| `exercise.is_planned_to_start` | legacy quiz field | client hits are a dropdown *string literal*, one commented-out line, and stale spec fixtures — not in the client model |
| `exercise.is_visible_before_start` | legacy quiz field | same |
| `jhi_user.external_llm_usage_accepted` | superseded consent handling | |
| `plagiarism_submission_element.model_element_id` | legacy | the client's `assessment.modelElementId` is an **Apollon** object property on a different table — unrelated |
| `programming_exercise_build_config.testwise_coverage_enabled` | testwise coverage removed | **zero `testwise` references remain** in `src/main/java`, `src/main/webapp`, `src/test` |
| `team_assignment_config.exercise_id` | relationship inverted; FK now lives on `exercise.team_assignment_config_id` | carries a stray UNIQUE index `UC_TEAM_ASSIGNMENT_CONFIG_EXERCISE_ID_COL`, dropped automatically with the column. **No FK** |
| `tutor_participation.points` | legacy | `TutorParticipation` has no `points` field |

### Columns — 2 that are a deliberate staged deletion, not an oversight

`iris_session.legacy_exercise_id` and `iris_session.legacy_lecture_id`. Changeset `20260410144433-8` says so explicitly:

> *Step 8: Rename obsolete columns instead of dropping them (rename-first delete policy). The actual `<dropColumn>` happens in a later release once we are confident no rollback is needed.*

Applied to production 2026-07-07. They still hold data — **359,948** rows with `legacy_exercise_id`, **75,346** with `legacy_lecture_id`, of 773,922 sessions. This is the only remaining record of which exercise or lecture a pre-migration Iris session belonged to.

**Needs an explicit go/no-go from the Iris owners**, not a silent drop. Production has since moved 9.7.1 → 9.8, so the rollback window has likely closed — but confirm the information was carried forward before discarding it.

### Tables — 4, no inbound foreign keys

| Table | Rows (prod) | Size | Why dead |
|---|---|---|---|
| `competency_learning_path` | 131,560 | 6 MB | `LearningPath.competencies` is `@Transient`. **No FK constraints at all** |
| `testwise_coverage_report_entry` | 6,496 | 1 MB | testwise coverage removed |
| `coverage_file_report` | 437 | < 1 MB | testwise coverage removed |
| `coverage_report` | 187 | < 1 MB | testwise coverage removed |

Their FKs point *outward* only (`coverage_report` → `submission`, `testwise_coverage_report_entry` → `programming_exercise_test_case` / `coverage_file_report`, `coverage_file_report` → `coverage_report`). **Nothing live references them**, so drop order is simply children first:

```sql
DROP TABLE testwise_coverage_report_entry;
DROP TABLE coverage_file_report;
DROP TABLE coverage_report;
DROP TABLE competency_learning_path;
```

Also remove their `createTable` / `createIndex` / `addForeignKeyConstraint` blocks from `00000000000000_initial_schema.xml` (lines 497, 2208, 3182, 3185, 3393, 3584, 3657, 3658, 3659) so fresh installs stop creating them — **do not edit the existing changeset**, its checksum is already recorded everywhere; add a new changeset instead.

### Assessment

| | |
|---|---|
| **Duration** | **< 10 s total.** `DROP COLUMN` is INSTANT on MySQL 8.0.29+, so table size is irrelevant — `feedback` at 8.5 GB drops as fast as a small table. Table drops are near-instant. |
| **Space reclaimed** | **Effectively none.** INSTANT `DROP COLUMN` leaves the data physically in the rows until the table is next rebuilt, and the four tables total ~7 MB. The value here is schema clarity, not disk. |
| **Risk** | **Low technically, irreversible for the data.** 138 K rows across the four tables plus the column values. |
| **Prerequisite** | Take a targeted dump of the four tables and the Iris legacy columns before dropping. |
| **Decisions needed** | (1) retention of `competency_learning_path` (131 K rows) for historical learning-analytics reporting; (2) Iris owners' go/no-go on the two `legacy_*` columns. |
| **Effort** | 0.5 day for the 12 columns + 4 tables; the Iris columns are a separate, gated change. |

---

## C6 — `@OrderColumn` on `mappedBy` associations: warning noise, not a bug

**Priority: LOW** — *corrected; an earlier draft of this document overstated this*

Hibernate logs `HHH160246` for eleven associations: *"is `mappedBy` another entity and should not specify an `@OrderColumn` (use `@OrderBy` instead)"*.

**An earlier draft of this document claimed Hibernate ignores these `@OrderColumn`s and that the ordering therefore is not maintained. That was wrong.** Checking the mapping model directly: all of these order columns **are** present in Hibernate's metadata, so they are read and written normally. Of the 21 `*_order` columns in the schema, **18 are mapped and live** — including `result.results_order`, which is mapped via `Submission.results` (`exercise/domain/Submission.java:97`).

Only **3** are unmapped, and all three are already covered by C5: `feedback.feedbacks_order`, `drag_and_drop_mapping.correct_mappings_order`, `short_answer_mapping.correct_mappings_order`.

The team is clearly already on top of this mapping class — see the extensive comments and workarounds at `quiz/repository/QuizExerciseRepository.java:189`, `quiz/service/QuizExerciseService.java:1311`, `quiz/web/QuizExerciseResource.java:142`, and `Lecture.java:71-76` (which documents deliberately *not* using `@OrderColumn`), plus issues #12574 / #12584 / #12610 / #12640.

**Recommendation:** no functional change. Optionally migrate the remaining `mappedBy` + `@OrderColumn` pairs to `@OrderBy` to silence the warning, but that is a code-quality exercise with real regression risk (it changes ordering semantics on user-visible quiz content), not a defect fix. The genuine defect in this area — the three orphaned columns — is C5.

---

## C7 — Remove the e2e seed changelog from `master.xml`

**Priority: MEDIUM** (security hygiene, trivial fix)

`20260304120000_e2e_seed_data.xml` is included unconditionally in `master.xml` and guarded **only** by `context="e2e"`. Liquibase runs **all** contexts when none is specified.

Production is safe because `application.yml:287` pins `contexts: prod`. But any manual Liquibase invocation without `--contexts` — the CLI, a Gradle task, an ad-hoc container — inserts:

- `artemis_admin` — activated, internal, **ADMIN authority**, bcrypt cost-4 hash committed in `src/main/resources/config/liquibase/e2e/users.csv`
- 6 further test users, 24 courses, 15 posts, conversations and conduct agreements

I triggered this myself while building the reference database for this analysis, which is how it was found.

**Fix:** move the include out of `master.xml` into an e2e-only changelog referenced by the e2e profile, or add a hard precondition (e.g. a marker row / `sqlCheck` on a dedicated flag) so the context label is not the only guard.

| | |
|---|---|
| **Production duration** | 0 s |
| **Risk** | Very low to fix; the current state is a low-likelihood / high-impact exposure |
| **Effort** | 30 min |

---

## C8 — Add drift detection to CI

**Priority: MEDIUM** (prevents recurrence)

None of Part 1 would have been caught by existing checks, because every check runs against a **fresh** database. `consolidate-changelogs.sh` compares new-vs-develop on fresh databases — by construction it cannot see production drift.

**Proposal:** a scheduled job that
1. builds a reference schema by running `master.xml` against a clean MySQL 8.0 container with production's charset/collation,
2. dumps `information_schema` from both it and production (read-only),
3. diffs columns / indexes / FKs / triggers with the two known normalizations (`NO ACTION`≡`RESTRICT`, display widths), and
4. fails or reports on any difference not on an explicit allow-list (which would start as **P8** — the 86 boolean columns — and the accepted items from P5/P6).

The full comparison used for this document runs in about 4 minutes and is entirely read-only against production.

| | |
|---|---|
| **Effort** | 1–2 days |
| **Value** | Turns a one-off audit into a standing guarantee |

---

# 3. Risk assessment summary

| ID | Fix | Severity if unfixed | Risk of fixing | Prod DDL time | Write-blocking? |
|---|---|---|---|---|---|
| **P1** | Enforce team/student invariant via denormalized `UNIQUE` | **High** — cross-table invariant is race-unsafe in prod and wholly unenforced everywhere else | Low–Med | < 10 s | No |
| ~~**P2-a**~~ | ~~`science_event.timestamp` → `datetime(3)`~~ | ✅ **Applied 2026-07-28** | — | done | — |
| ~~**P2-b–f**~~ | ~~5 remaining precision fixes~~ | ✅ **Applied 2026-07-28** | — | done | — |
| ~~**P3**~~ | ~~Drop 3 redundant indexes~~ | ✅ **Applied 2026-07-28** | — | done | — |
| ~~**P4**~~ | ~~Normalize 4 constraint names~~ | ✅ **Applied 2026-07-28** | — | done | — |
| **P5** | Align composite PK order | Low | **Medium** | < 15 s | Yes (seconds) |
| ~~**P6**~~ | ~~Collation fork~~ | ✅ **Applied 2026-07-28** | — | done | — |
| **P7** | Drop 4 dead tables | Low | Low, **irreversible** | < 2 s | No |
| **P8** | Boolean normalization | None | **High** (3–8 h downtime) | — | — |
| **C1** | `result_rating` unique in changelog | Medium | Very low | 0 s on prod | No |
| **C2** | 6 mis-annotated `@OneToOne` | Medium — latent | Low | 0 s | No |
| **C3** | Add 16 verified unique constraints | Medium | **Medium** (may fail at deploy) | ~1–2 min | No |
| **C4** | 22 `NOT NULL` + 3 backfills | Medium | **Medium** (may break writers) | 30–90 s | No |
| **C5** | Drop 14 dead columns + 4 dead tables | Low | Low, **irreversible** | < 10 s | No |
| ~~**C6**~~ | ~~Ignored `@OrderColumn`~~ — **corrected: not a bug**, 18 of 21 order columns are mapped and live | none | — | — | — |
| **C7** | e2e seed guard | Medium (security) | Very low | 0 s | No |
| **C8** | CI drift detection | — | None | — | No |

### Highest-risk items to fix
- **C3 / C4** — verified against today's data; a race or an unnoticed writer turns them into a **startup-blocking deploy failure**. Ship them isolated from other changesets.
- **P5** — PK changes interact with FK-backing indexes; benefit does not justify it.

### Highest-risk items to *skip*
- **P8** — correctly skipped, but every future changeset author must know booleans are `bit(1)` in production.
- **P2-a** — skipping leaves a permanent prod-vs-test divergence in a 15 M-row table.

---

# 4. Time and effort summary

### Production DDL execution time

| Phase | Contents | Wall clock | Write-blocked |
|---|---|---|---|
| **Fast, online** | P1, P4, P7, C1, C5 + C4's 22 columns (~~P3~~ ✅ done) | **~2 min** | ~0 s |
| *(of which P1)* | *column + backfill + unique index + triggers* | *< 10 s* | *~0 s* |
| ~~Short window~~ | ~~P2-b … P2-f~~, ~~P6~~ — both ✅ done | — | — |
| **Constraints (isolated)** | C3, C4 backfills | **~2 min** | ~0 s |
| ~~Maintenance window~~ | ~~**P2-a** (`science_event`, 763 MB)~~ | ✅ **done** | — |
| **Deferred / skipped** | P5, P8 | — | — |
| **TOTAL remaining** | | **~4 min** | **~5 s** |

Disk headroom needed: ~800 MB peak (P2-a). 35 GB available — comfortable.

### Engineering effort

| Workstream | Effort |
|---|---|
| **P1** migration + dual-dialect triggers + DB-level integration test | **1 day** |
| P2–P4, P7 changesets + preconditions + `updateSQL` review | 1 day |
| C1, C2, C7 (annotation + changelog fixes) | 0.5 day |
| C3 + C4 (verification, backfills, `participant_score` investigation) | 1 day |
| C5 (dead schema removal + product sign-off) | 0.5 day |
| ~~C6~~ (`@OrderColumn`) — **withdrawn, not a defect** | — |
| C8 (CI drift detection) | 1–2 days |
| Multi-node E2E validation (`run-e2e-tests-local-multinode.sh`) | 0.5 day |
| **TOTAL** | **~7–9 working days** |

---

# 5. Recommended sequencing

**Release 1 — safe, online, no decisions needed** (~2 min DDL)
`P1` · `C1` · `C2` · `C7` · **`P3` changesets** (production DDL already done — these `MARK_RAN` here and execute on other installations)
Highest value, lowest risk. `P1` closes a real multi-node data-integrity gap — one that production only *partially* mitigates and other environments do not mitigate at all; `C2` makes the schema *verifiable* again. Do those two first.

> `P1` is the one item that **changes behaviour in CI, dev and PostgreSQL** (they start enforcing an invariant they previously did not) and the one item that **adds a constraint which can fail at deploy time**. Budget a full test-suite run plus `run-e2e-tests-local-multinode.sh`, re-verify §7 immediately before deploying, and expect to fix any fixture that puts one student in two teams of the same exercise.

**Release 2 — naming, plus recording the completed precision work** (~10 s DDL)
`P4` · **`P2` + `P3` changesets, precondition-guarded** (~~`P6`~~ ✅ done, no changeset needed) — production DDL is already done (§6), so these `MARK_RAN` here and execute on other installations.

**Release 3 — constraints, isolated** (~2 min DDL)
`C3` then `C4`, in **separate changesets**. Re-run the duplicate and NULL checks (§7) immediately before deploying. Isolation matters: these are the two changesets that can fail at startup.

**Release 4 — cleanup, after product sign-off** (~10 s DDL)
`P7` + `C5` together, with an archive dump taken first.

**Backlog**
`C8` (prevents recurrence) · `P5`, `P8` (documented as accepted divergence).

---

# 6. Recording the completed P2 work in Liquibase

**The production DDL for P2 is already done** (applied manually 2026-07-28, verified). What remains is to express it as changesets so that the change is recorded and **other long-lived Artemis installations converge**. Fresh installs need nothing — `initial_schema.xml` already declares `datetime(3)`.

Ship each column with a precondition that inspects the *actual* precision. On this production instance and on fresh installs the precondition is already satisfied, so the changeset `MARK_RAN`s; on an installation still carrying the old precision it executes:

```xml
<changeSet id="…-science-event-timestamp-precision" author="…">
    <preConditions onFail="MARK_RAN">
        <sqlCheck expectedResult="0">
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'science_event'
              AND COLUMN_NAME = 'timestamp' AND DATETIME_PRECISION = 3
        </sqlCheck>
    </preConditions>
    <modifyDataType tableName="science_event" columnName="timestamp"
                    newDataType="datetime(3)"/>
    <addNotNullConstraint tableName="science_event" columnName="timestamp"
                          columnDataType="datetime(3)"/>
</changeSet>
```

Note `information_schema.COLUMNS` and `DATETIME_PRECISION` are portable across MySQL and PostgreSQL, but `DATABASE()` is not — use `current_schema()` on PostgreSQL, or split into two `dbms`-scoped preconditions. PostgreSQL spells the type `timestamp(3)`, so the `modifyDataType` needs `dbms`-scoped variants too.

Always check `updateSQL` output before shipping: `modifyDataType` does **not** preserve nullability on all dialects, which is exactly why the manual run was verified for `NOT NULL` afterwards.

### For any installation that still has to run this

`science_event` was the expensive one — 14.9 M rows / 763 MB, `ALGORITHM=COPY`, writes blocked for an estimated 2–6 minutes. Because Liquibase holds `DATABASECHANGELOGLOCK` during startup, a changeset that long delays **every** node in the cluster. The approach used successfully here was:

1. Run the `ALTER` manually in a maintenance window.
2. Let the precondition-guarded changeset `MARK_RAN` on the next deploy.

**Alternative** if a write-blocking window is unacceptable: `pt-online-schema-change` (**not currently installed** on the DB host, and it requires triggers on `science_event` for the duration). For a 763 MB append-only telemetry table, the short direct window proved simpler.

---

# 7. Verification queries

Re-run these immediately **before** deploying C3 and C4 — the safety of both depends on data that can change.

```sql
-- C3: every one of these must return 0
SELECT COUNT(*) FROM (SELECT attachment_unit_id FROM attachment
  WHERE attachment_unit_id IS NOT NULL
  GROUP BY attachment_unit_id HAVING COUNT(*) > 1) d;
-- …repeat for the other 15 constraints in C3

-- C4: every one of these must return 0
SELECT SUM(user_id IS NULL) FROM iris_session;
SELECT SUM(session_id IS NULL) FROM iris_message;
-- …repeat for the other 20 safe columns

-- P3: confirm the covering duplicate still exists before dropping
SHOW INDEX FROM jhi_user;
SHOW INDEX FROM competency_user;
SHOW INDEX FROM participation_vcs_access_token;

-- P1: confirm the invariant currently holds before re-declaring the trigger
--      (must return 0; production returned 0 at the time of writing)
SELECT COUNT(*) FROM (
  SELECT t.exercise_id, ts.student_id
  FROM team_student ts JOIN team t ON t.id = ts.team_id
  GROUP BY t.exercise_id, ts.student_id
  HAVING COUNT(DISTINCT ts.team_id) > 1
) v;

-- P1: current trigger inventory (prod has exactly one; fresh installs have none)
SELECT TRIGGER_NAME FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = 'Artemis';
```

---

## Appendix — accepted divergences (allow-list for C8)

| Divergence | Count | Rationale |
|---|---|---|
| Boolean columns `bit(1)` (prod) vs `tinyint` (fresh) | 86 across 38 tables | Normalization = 3–8 h downtime, ≥ 18 GB of rebuilds, zero functional benefit (**P8**) |
| Column ordinal positions | 132 | Cosmetic; Hibernate always names columns |
| Composite PK column order | 4 tables | Fix risk exceeds benefit (**P5**) |
| `NO ACTION` vs `RESTRICT` in FK rules | all FKs | Identical InnoDB behaviour; a reporting artifact only |
| Integer display widths | all | MySQL 8 no longer reports them |
