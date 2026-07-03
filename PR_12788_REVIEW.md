# Deep Review — PR #12788: Replace course group strings with `UserCourseRole`

> **Status:** Draft feedback for author review — **not yet posted to GitHub.**
> **PR:** https://github.com/ls1intum/Artemis/pull/12788 (`chore/migrate-course-groups-to-user-course-roles` → `develop`)
> **Diff base:** merge-base `61c3402` · **Scope:** full PR (523 files, +5323/−6048), deep focus on Liquibase / DB / Hibernate and performance.
> **Method:** 6 dimension specialists reading the checked-out branch; every medium+ finding adversarially re-verified against the source. The `repository-queries` dimension hit a tooling error, so those items (L11/L12) rest on manual spot-checks rather than the verified panel.

## Verdict

A **well-executed, semantically-correct refactor** — the domain model is genuinely better and auth-equivalence is preserved. Two things must be addressed before merge:

1. **It is a *full cutover*, not the "dual-write kept until follow-up" the PR description claims.** `User.groups` (`@ElementCollection`) and all four `Course.*_group_name` fields are **deleted** from the entities. Defensible design, but the description is materially wrong and it changes the rollback story.
2. **Real performance regressions on hot paths** — most importantly the course dashboard — where in-memory `Set.contains` became per-call DB `EXISTS`. Given the server guideline ("very good performance… prevent too many DB calls"), these are merge-blockers.

No data-loss or security hole found. The `course.*_group_name` columns are nullable (`varchar(255)`, no NOT NULL), so the now-unmapped columns do **not** break `Course` inserts.

---

## 🔴 High — should fix before merge

### H1. Course dashboard fires 1–2 `EXISTS` **per active course on the entire instance** (largest regression)
`course/service/CourseVisibleService.java:36-47`, driven by `CourseService.findAllActiveForUser` (`:218`) and `findAllActiveWithExercisesForUser` (`:231`), reached from `/courses/for-dashboard`, `/courses/for-dropdown`, `/courses/for-notifications`.

`courseRepository.findAllActive()` has **no user predicate** — it returns every active course on the server — then `.filter(isCourseVisibleForUser)` runs `isAtLeastTeachingAssistantInCourse` (1 `EXISTS`) and, for the common student case, `isStudentInCourse` (a 2nd `EXISTS`) per course. Old code resolved both in-memory via `user.getGroups().contains(...)` = **0 extra queries**. At scale (≈500 active courses) that's up to ~2N synchronous round-trips on the three most-hit authenticated endpoints, per request, per user.

**Fix:** bulk-load the user's roles once — `Map<courseId, Set<CourseRole>>` via a single `findByUser_Id(userId)` (or `…AndCourse_IdIn`) — and resolve visibility in memory; or push a `user_course_role` join into `findAllActive` so the DB returns only the user's visible courses. *(verified: confirmed, high)*

### H2. `addUserToCourse`/`removeUserFromCourse` reload-by-login + 3 `EXISTS` + `saveUser` on **every** call, even when nothing changed — and run in bulk loops
`account/service/user/UserService.java:592-615`.

Each call: 1 `EXISTS` + conditional insert, then **unconditionally** `findOneWithAuthoritiesByLogin` + `buildAuthorities` (3 `EXISTS`) + `saveUser` (UPDATE). The old `addUserToGroup` short-circuited to **zero work** for an already-member (`if (!user.getGroups().contains(group))`) and reused the loaded user. For STUDENT the authority set never changes, yet it's rebuilt and the user row re-saved each iteration. Bulk callers: `CourseAccessService.registerUsersForCourse:138` and `ExamRegistrationService.registerStudentsForExam:119` (the latter adds a further per-student `isInstructorInCourse` `EXISTS` at `:129`). A 300-student CSV re-upload ≈ **~1800 reads + 300 UPDATEs** in a serial loop.

**Fix:** return early when `existsBy…` is already true; only reload+rebuild+save authorities when a role actually changed *and* the coarse authority set could change (skip for STUDENT); drop the redundant `findOneWithAuthoritiesByLogin` (caller passes a loaded user). *(verified: confirmed, high)*

### H3. Backfill string-join is **case/accent-insensitive on MySQL** but the old check was exact — can grant roles users never had, invisible in CI
`resources/config/liquibase/changelog/20260518120000_changelog.xml:95-111` (changeSet `-2`).

Prod MySQL runs `--collation-server=utf8mb4_unicode_ci` (`docker/mysql.yml:20`) and no column sets an explicit `COLLATE`, so `c.student_group_name = ug.user_groups` matches `'TUMuser'` to `'tumuser'`, `'Café'` to `'cafe'`. The behavior it must reproduce — Java `Set<String>.contains()` — is byte-exact. PostgreSQL (Testcontainers) is also case-sensitive, so **tests pass while prod over-matches**. A prior migration (`20260410144433_changelog.xml:32`) already documents/works around this exact MySQL-vs-PostgreSQL collation difference; this one doesn't.

**Fix:** force a deterministic comparison on the MySQL arm — `… = ug.user_groups COLLATE utf8mb4_bin` (split the `<sql>` into `dbms="mysql,h2"` / `dbms="postgresql"` arms as the create-table step already does). Query a prod snapshot for case/accent-only collisions first. *(verified: confirmed, high)*

---

## 🟡 Medium

- **M1. Description says "dual-write kept"; code is a clean cutover → rollback unsafe.** `User.groups`/`UserGroup.java` and `Course.*_group_name` removed; zero reads/writes of the legacy structures remain. The legacy table/columns are frozen until CS-4, so a rollback after any enrollment change reads stale membership. *Fix:* correct the description + rollback narrative, or actually implement the dual-write; confirm no out-of-band consumer reads the legacy columns. *(confirmed; adjusted high→medium)*
- **M2. Backfill joins on unindexed `course.*_group_name` and runs at startup (blocks boot), MySQL-only.** Likely a hash join (seconds, not minutes), but untested in CI. *Fix:* throwaway index before the backfill (drop in CS-4) or pre-select the small `course` side into a CTE; validate with `EXPLAIN` on a prod dump. *(confirmed; adjusted high→medium)*
- **M3. No automated test exercises the backfill (CS-2/CS-3).** With a full cutover, backfill correctness alone decides whether existing users keep access; test seeding writes `user_course_role` directly and never via `user_groups`+migration. *Fix:* a Liquibase-harness test asserting backfill rows == old group semantics (shared-group + `lti_created` cases). *(confirmed, medium)*
- **M4. No request-scoped memoization → 3–4 identical-target `EXISTS` for the same `(user, course)`.** `ChannelResource.getCourseChannelsOverview:127` = 4 `EXISTS`; `CalendarResource.getCalendarEventSubscriptionFile:135` = 3 (with a duplicate TA check). `isOnlyStudentInCourse` went 0→2 queries (10 call sites). *Fix:* request-scoped `(userId,courseId)→Set<CourseRole>` cache; drop the duplicate TA query. *(confirmed, medium)*
- **M5. `findAllEnrollableForUser` — one `EXISTS` per enrollment-active course (N+1).** `course/service/CourseAccessService.java:84-87`. *Fix:* fetch the user's STUDENT course-ids once, filter in memory. *(confirmed, medium)*

---

## 🟢 Low / Nits

- **L1.** `UNION` → `UNION ALL` in the backfill (arms provably disjoint, each already `DISTINCT`): byte-identical, strictly cheaper. *(verified low)*
- **L2.** Course-delete progress bar can never reach 100% — `CourseOperationWeights.calculateDeletionWeight()` still adds `WEIGHT_USER_GROUPS` though its step was removed. Concrete cosmetic bug + dead constant.
- **L3.** `User.courseRoles` `cascade=REMOVE` redundant/asymmetric vs `Course.courseRoles` (no cascade) + DB FK + explicit bulk delete; only hard-delete path is never-activated-user cleanup (≈0 roles). Drop the cascade for consistency. *(adjusted medium→low)*
- **L4.** `fetch=LAZY` on the two `@Id @ManyToOne` (`UserCourseRole.java:26,31`) is silently ignored (id associations are always eager) — misleading.
- **L5.** `equals`/`hashCode` key on `getId()` — unsafe for transient (null-id) instances in a `HashSet`; current paths never hit it, but a footgun.
- **L6.** `addUserToCourse`/`removeUserFromCourse` non-transactional; `removeUserFromCourse`'s `orElseThrow` after a committed `@Modifying` delete can 500 the caller despite success. Self-healing.
- **L7.** ENUM (MySQL) vs `varchar(20)` (Postgres) divergence: a future >20-char role truncates on Postgres / a new role needs a MySQL `ENUM ALTER`. Consider `varchar` on both.
- **L8.** No `<rollback>` on data changesets `-2`/`-3`.
- **L9.** Out-of-order changeset id (`20260518120000` before merged `20260527…`/`20260611…`) — benign under Liquibase defaults.
- **L10.** `LtiService.enrollUserInCourse:170` hand-rolls the `addUserToCourse` sequence instead of delegating; duplicated logic.
- **L11.** *(manual)* `ConversationDTOService.getChatParticipantDTOs:279` calls `findByIdWithCourseRolesAndAuthoritiesElseThrow` per participant when roles aren't preloaded — bounded to small/one-to-one chats; old code had a similar lazy-load. Candidate to batch.
- **L12.** *(manual)* `findByCourse_IdAndRole` and role-list paths no longer filter `deleted=false` explicitly; they rely on `softDeleteUser` having removed the user's `user_course_role` rows. Holds today, but implicit — add a comment/test.

---

## ✅ Done well

- **Composite `@IdClass` derived-identity mapping is correct** (canonical Hibernate 6/7 pattern); `@Enumerated(STRING)` fits both column types.
- **`AuthorityService` is a net improvement** — 3 small `EXISTS` index seeks instead of loading all instructor/editor/TA group-name strings.
- **Role counts batched well** — `countAllRolesByCourseIds` is a single `GROUP BY` on the covering index.
- **User search uses correct two-step pagination** (`findUserIds…` → `findUsersByIdsWithCourseRolesOrdered`), avoiding the JOIN-FETCH-with-`Pageable` pitfall.
- **Course reset now correctly course-scoped** — fixes a latent bug where the old `removeGroupFromAllUsers` unenrolled across every course sharing a group name.
- **Deletion cascades correct** — DB FK cascade on course delete, explicit bulk delete on soft-delete; no double-handling.
- **Auth semantics preserved** — dropping `|| isSuperAdmin(user)` is not a regression (`isAdmin` covers super-admins, `AuthorizationCheckService:644`); `isAtLeast*` role sets are exactly equivalent; `lti_created` wired end-to-end.

---

## Suggested ask to the author

Before merge: (1) batch-load roles for the dashboard/enrollment loops (H1, M5) and make add/remove idempotent for bulk enrollment (H2); (2) fix the MySQL collation in the backfill (H3); (3) correct the PR description re cutover-vs-dual-write + rollback (M1); (4) add a backfill equivalence test (M3). `UNION ALL` (L1) and the progress bar (L2) are trivial wins.

> **Caveats:** the `repository-queries` sweep is covered by manual spot-checks (panel agent errored). PR is currently CONFLICTING with `develop` and CI had test failures, so findings may shift after a rebase.
