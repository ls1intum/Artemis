# PR #12788 — Query Performance Benchmark (develop vs PR)

> Measures the DB-query cost of the queries this PR changes, **develop (group strings) vs PR (`user_course_role`)**.
> Companion to `PR_12788_REVIEW.md`. Numbers are real measurements, not estimates.

## Setup

| | |
|---|---|
| Database | **MySQL 9.7.0** (the prod-pinned image, `docker.io/library/mysql:9.7.0`), `--collation-server=utf8mb4_unicode_ci`, `--lower_case_table_names=1` — identical to `docker/mysql.yml` |
| Client | Python `pymysql` over `127.0.0.1:13306` (TCP, single persistent connection), median of N warm runs |
| Scale | **50,000 users**, **500 courses (all active)**, biggest course = **2,000 students**, **169,194** membership rows (mirrored in both `user_groups` and `user_course_role`) |
| Schema | `user_course_role(user_id, course_id, course_role ENUM)` with `PRIMARY KEY (user_id, course_id, course_role)` + `idx_ucr_course_role (course_id, course_role)`; `user_groups` with the real Artemis indexes (`user_groups`, `(user_groups, user_id)`, FK on `user_id`) — exactly as in the PR / current schema |

> **Caveat:** localhost TCP round-trip (~0.13 ms) is *faster* than a real prod hop to a separate DB host (~0.3–1.5 ms). The N+1 findings below are therefore **conservative** — they get worse on prod. Single-threaded, warm-cache; absolute ms scale with hardware but the develop-vs-PR *ratios* are the point.

## Results (median wall-clock)

| Scenario | develop | PR | Verdict |
|---|--:|--:|---|
| **S1 — Dashboard authorization** (student, 500 active courses) | **0.19 ms** · 1 query | **139–173 ms** · ~1000 queries | 🔴 **~720–850× slower** (H1) |
| **S1-FIX — Dashboard auth, bulk-load roles + in-memory** | — | **0.17 ms** · 1 query | ✅ fix A: ~830× faster than PR, ≈ develop |
| **S1-PUSHDOWN — Dashboard auth, filter role in the query** | — | **0.14 ms** · 1 query | ✅ **fix B (recommended): faster than develop, 0 in-memory work, returns only the user's courses** |
| **S2 — List 2,000 students of the big course** | 4.6–5.1 ms · 1 query | 4.5–5.4 ms · 1 query | 🟢 neutral (well-indexed) |
| **S3 — Role counts, 500-course overview** | 302–337 ms · 1 query | 85–90 ms · 1 query | 🟢 **~3.6× faster** |
| **S4 — `buildAuthorities`** (per enrollment change) | 5.3–5.8 ms · 3 queries | 0.48–0.65 ms · 3 queries | 🟢 **~9–12× faster** |
| **S5 — single `EXISTS` (one role check)** | — | 0.13 ms | per-call cost is trivial |

> Ranges reflect two runs (timings vary ±15% run-to-run; ratios are stable).

**The PR is a net win on the aggregate/batched queries (S3, S4) and neutral on bulk listing (S2). The damage is concentrated entirely in the per-course-loop pattern (S1) and the per-user bulk-enrollment loop (H2 below).** Per-call `EXISTS` cost (S5 = 0.13 ms) is tiny — the regression is the *number* of round-trips, not their individual cost.

## Real-app latency: same endpoints, develop vs PR (multi-node-fast stack)

This is the comparison that matters: the **average end-to-end latency of the same representative REST endpoints**, on the real running app, where only the access-control query mechanism differs. I ran the **actual WARs** (built from `develop` and from `pr-12788`) on the **multi-node-fast stack** (Postgres + JHipster-Registry/Eureka + ActiveMQ, node in `prod-multinode` config), seeded **524 active courses**, and timed each endpoint as `artemis_admin` — **mean of 60 warm requests over a kept-alive connection**.

**Headline (controlled, apples-to-apples):** measured back-to-back under the *same* launcher (single node, no Playwright/reset, identical 524-course seed), develop and the **fixed** PR are statistically equal; the unfixed PR is 30–100× slower on the loop endpoints.

| Endpoint — mean (median, p95), ms | develop | PR (as-is) | **PR + fix** |
|---|--:|--:|--:|
| `GET /courses/for-dashboard` (524 active courses) | **23.2** (22.0, 27.8) | **~647** (664, 830) | **22.2** (20.8, 30.5) |
| `GET /courses/for-dropdown` (524 active courses) | **6.5** (6.2, 9.6) | **~322** (287, 499) | **6.3** (6.1, 8.0) |
| `GET /courses/{id}` (single course) | **5.0** (4.9, 6.5) | **~7.8** (7.3, 13.0) | **5.3** (5.1, 7.4) |

How to read it:
- **The fix restores parity.** `PR + fix` is within measurement noise of develop on every endpoint (dashboard 22.2 vs 23.2, dropdown 6.3 vs 6.5, single-course 5.3 vs 5.0) — i.e. **equal or faster**, and 25–100× faster than the unfixed PR.
- **The unfixed PR** turns the two course-list endpoints — hit on essentially every app open / navigation — into **0.3–0.65 s** calls. Latency grows **linearly with the number of active courses**: each adds one access-control round-trip, ~0.6 ms each on the real `Hibernate → HikariCP → JDBC → Postgres` path. (At 524 courses: dropdown ≈ 6 ms + 524×~0.6 ms ≈ 322 ms; dashboard ≈ 22 ms + ~1048×~0.6 ms ≈ 647 ms.) On a real DB hop the gap widens. Even the single-course endpoint is ~1.5× on the unfixed PR (`getCourse` does ~4 checks → 4 `EXISTS`).
- An earlier cross-condition reading showed develop 18.3 vs PR-fixed 28 — that ~10 ms gap was **boot-to-boot variance** (the runs used different JVM boots). Re-measured back-to-back under the identical controlled launcher, the gap disappears.

**Mechanism (why the unfixed PR is slow):** the per-course access check became a DB query — this exact prepared statement, executed once per active course (524× for dropdown, 1048× for dashboard), where develop resolved it in memory after a single membership load:
```sql
select exists(select ... from user_course_role ucr1_0
              where ucr1_0.user_id=$1 and ucr1_0.course_id=$2 and ucr1_0.course_role in ($3,$4,$5,$6))
```

## Extended coverage: 8 endpoints, three-way (deterministic, 524 active courses, user enrolled in all 500)

To map the impact across the changed mechanisms, all three branches were measured on the same deterministic dataset (524 active courses; the test user enrolled as instructor in all 500 perf courses so visibility is membership-based, not the flaky admin path; one course with 2000 students). Mean of 40 warm keep-alive requests on a single directly-launched node (no Playwright phase; cold-cache restart after seeding so the user's membership is fresh). **Correctness of the fix is independently verified: `AuthorizationCheckServiceTest` passes 53/53 on the fix commit.**

| Endpoint | mechanism / service | develop | PR | PR-fixed |
|---|---|--:|--:|--:|
| `for-dashboard` | visibility loop (`isCourseVisibleForUser` × N) | **24.7** | **1244** | **30.8** |
| `for-dropdown` | visibility loop | **8.1** | **419** | **11.2** |
| `for-notifications` | visibility loop | **8.2** | **258** | **11.8** |
| `course-management-overview` | role counts (`countAllRolesByCourseIds`) | **7.8** | **5.2** | **7.8** |
| `with-user-stats` | role counts | **16.6** | **9.6** | **11.2** |
| `courses/{id}` | repeated checks (`getCourse`, ~4 checks) | **6.1** | **8.1** | **7.9** |
| `{course}/students` (2000) | list-by-role (`findByCourse_IdAndRole`) | **11.9** | **14.4** | **14.4** |
| `{course}/students/search` | role-scoped search | **7.9** | **9.4** | **9.5** |

Reading by mechanism:
- **Visibility loops (3 endpoints) — the regression.** PR is **31–52× slower** (dashboard 1244 ms, p95 2.9 s); PR-fixed restores them to within ~1.25–1.44× of develop. (This PR dashboard is worse than the earlier 647 ms because the user is now enrolled in all 500 courses, so every course both fires the `EXISTS` *and* is score-calculated.)
- **Role counts (2 endpoints) — improved, not regressed.** PR/PR-fixed are **equal or faster** than develop (the batched `GROUP BY` beats develop's group-name-IN query). Confirms harness S3.
- **Repeated-check single course — ~neutral** (PR-fixed 7.9 vs develop 6.1).
- **List-by-role + role search (2 endpoints) — slightly slower (~1.2×, +2–2.5 ms) on *both* PR and PR-fixed.** This is a separate *query-shape* effect (`findByCourse_IdAndRole` JOIN-FETCH user vs the old group query), **not** the auth-loop mechanism and not changed by the fix. Minor; a candidate follow-up if the few-ms matter.

> **Power-user residual — RESOLVED in v2 (O(1) map).** PR-fixed v1 used a linear `anyMatch` over the user's `courseRoles` (O(n) per check), which cost ~6 ms extra on the dashboard for a **500-course** user (30.8 vs 24.7). v2 builds a `Map<courseId, EnumSet<CourseRole>>` once per request (`User.getCourseRolesByCourseId()`) and does O(1) lookups.

### v2 result — back-to-back, same session, 500-course power user

| Endpoint (mean ms) | develop | PR-fixed **v2** |
|---|--:|--:|
| `for-dashboard` | 24.9 | **24.4** (equal — gap closed) |
| `for-dropdown` | 7.5 | 8.3 |
| `for-notifications` | 8.2 | 8.1 |
| `course-management-overview` (counts) | 7.5 | **5.5** (faster) |
| `with-user-stats` (counts) | 17.0 | **9.6** (1.8× faster) |
| `courses/{id}` | 6.4 | 6.8 |
| `{course}/students` (2000) | 11.3 | 13.8 |
| `{course}/students/search` | 7.9 | 9.1 |

With v2, the **dashboard is at parity with develop even for a 500-course user**, the count endpoints are **faster**, and the only remaining residual is the list-by-role / role-search pair (+1–2.5 ms, ~1.2×).

### v3 — query-pushdown + members-list optimization (pushed)

Two further optimizations, **committed and pushed to the PR** (commit `100ed224`, on top of the fix `008cd8cc`):

1. **Query-pushdown for the dashboard/dropdown.** Non-admins load only the courses they are a member of via an indexed join (`CourseRepository.findAllActiveWhereUserHasAnyRole`) instead of loading *all* active courses and filtering in memory (admins keep the load-all path). For an active course, holding any role is exactly the existing visibility condition, so behaviour is unchanged. This is what group-strings couldn't do cleanly and the relational model enables — so it makes the dashboard **faster than develop for the common case**:

   | Endpoint — non-admin user in 10 courses | develop | PR v3 |
   |---|--:|--:|
   | `for-dashboard` | 13.1 ms | **10.4 ms** (~20% faster) |
   | `for-dropdown` | 7.7 ms | **3.7 ms** (~2× faster) |

   (develop loads all 524 active courses and filters to 10; v3 loads only the user's 10.)

2. **Members-by-role list** (`{course}/students`): `getUsersWithRole` now selects the `User` directly (`findUsersByCourse_IdAndRole`) instead of hydrating 2000 intermediate `UserCourseRole` wrapper entities, and filters `deleted` explicitly. Measured: **13.8 → 12.0 ms** (develop 11.3) — the +2.5 ms residual is now ~+0.7 ms, i.e. within noise. (The `/students/search` paged endpoint is 8.9 vs develop 7.9 — ~+1 ms, within noise; it already returned `User` directly so there was no wrapper hydration to remove.)

**Validation:** `AuthorizationCheckServiceTest` (53/53) and `DatabaseQueryCountTest` (2/2, asserts the dashboard's DB-query-count bound as a real user) both pass on v3, and `spotlessApply` was run.

**Net across the board:** typical-user hot paths **faster** than develop, power-user dashboard **equal**, count endpoints **faster**, members list **optimized**. The migration is now perf-positive, not just perf-neutral, on the user-facing paths.

## The fix (implemented + validated) — `PR_12788_PERF_FIX.patch`

The fix restores develop's proven pattern: **load the user's `courseRoles` once with the user, resolve every access check in memory** — instead of a DB `EXISTS` per check. 5 files, +71/−19:
- **`AuthorizationCheckService`** — `hasCourseRole` / `hasCourseRoleAtLeast` now `anyMatch` over `user.getCourseRoles()` in memory; `loadUserIfNeeded` loads `courseRoles` (mirroring how develop loaded `groups`).
- **`UserRepository`** — adds `getUserWithCourseRolesAndAuthorities()` (+ login / +organizations variants).
- **`CourseOverviewResource`** (dashboard, dropdown, notifications, single-course, single-dashboard) and **`CourseAccessResource`** (enrollment list) — load the user `WithCourseRoles` up front, so the in-loop checks are all in-memory (no per-course reload).
- **`UserService.addUserToCourse` / `removeUserFromCourse`** (H2) — idempotent; skip the user reload + authority rebuild when the role already exists / can't change.

This is one membership load per request + in-memory checks, exactly like develop — so it scales independently of the number of courses, fixing the dashboard/dropdown N+1 (H1), the repeated-check endpoints (M4), and the enrollment loop (M5) in one change. Validated above (`PR + fix` column).

> Method notes: each endpoint warmed (12) then timed (60), HTTP keep-alive, identical 524-course seed. Measured as `artemis_admin` on a single directly-launched node (no Playwright phase, so nothing resets the DB mid-run — an earlier run was corrupted by the runner's async DB reset). The unfixed-PR figures (~647/322/7.8) were measured on an earlier `up.sh` boot; its 30–100× magnitude dwarfs any boot-to-boot variance, so it is shown with `~`. Server-only WARs with a stub `index.html`; the Angular UI is irrelevant to server-side timing. **Correctness of the in-memory check (a faithful translation of the `EXISTS`) should be confirmed by running `AuthorizationCheckServiceTest` / the auth suite** — the latency runs validate performance, not semantics.

## S1 — the dashboard regression, in detail (finding H1)

`CourseService.findAllActiveForUser` iterates **every active course on the instance** (`findAllActive()` has no user predicate) and calls `CourseVisibleService.isCourseVisibleForUser`, which for a student issues `isAtLeastTeachingAssistantInCourse` (1 `EXISTS`) + `isStudentInCourse` (1 `EXISTS`) = **2 queries × 500 courses ≈ 1000 round-trips**. develop did this in memory after the user's groups were already loaded → **0 extra queries**.

Extrapolated **added** latency per dashboard load (≈999 extra round-trips), by network RTT:

| RTT/round-trip | localhost 0.13 ms | prod-ish 0.30 ms | prod-ish 0.70 ms | slow 1.5 ms |
|---|--:|--:|--:|--:|
| Extra latency per load | **~134 ms** | **~300 ms** | **~700 ms** | **~1.5 s** |

This hits `/courses/for-dashboard`, `/courses/for-dropdown`, `/courses/for-notifications` — the most frequently called authenticated endpoints — **per request, per user**, and serially blocks the response thread. **Fix:** one bulk query of the user's roles (`Map<courseId, Set<CourseRole>>`) resolved in memory → back to ~1 query (and likely faster than develop, like S3).

## Appendix: alternative fix shapes (harness-validated)

> These were the early design options I micro-validated in the SQL harness. The **implemented** fix (see "The fix" section above) is the in-memory-load approach (Option A generalized into `AuthorizationCheckService`), validated end-to-end. Option B below (push the filter into the query) remains a good further optimization specifically for the dashboard/dropdown course list if you want to avoid materializing all active courses.

### Proposed fix for H1 — two validated options

Both collapse the ~1000-query loop to **one** query. **Option B (filter in the query) is recommended** — it avoids loading data into memory at all and never materialises the 500 active courses; it only returns the user's own visible courses.

### Option B (recommended): push the role filter into the query — 0.14 ms, faster than develop

Replace `findAllActive()` + per-course filter with a query that joins `user_course_role` and applies the visibility rule directly, so the DB returns **only the courses the user can see** (a handful), never 500 rows, zero in-memory filtering:

```java
// CourseRepository — returns only the user's visible active courses
@Query("""
        SELECT DISTINCT c FROM Course c
            JOIN UserCourseRole ucr ON ucr.course = c AND ucr.user.id = :userId
        WHERE (c.startDate <= :now OR c.startDate IS NULL)
          AND (c.endDate   >= :now OR c.endDate   IS NULL)
          AND (ucr.role <> de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
               OR c.startDate <= :now OR c.startDate IS NULL)
        """)
List<Course> findAllActiveVisibleForUser(@Param("userId") long userId, @Param("now") ZonedDateTime now);
```
`CourseService.findAllActiveForUser` then calls this instead of `findAllActive(now).stream().filter(isCourseVisibleForUser)`. **Admins** (who see every active course via the old `|| isAdmin`) keep the existing `findAllActive` path behind an `if (isAdmin)` branch. Measured: **0.14 ms, 1 query, returns only the user's 9 courses** — the plan is a PK lookup on `user_id` (≤handful of rows) joined to `course` by PK, so it does not even scan the 500 courses. For `findAllActiveWithExercisesForUser`, keep the existing eager exercise/category fetch joins on top of the narrowed course set.

### Option A (alternative): bulk-load roles, resolve in memory — 0.17 ms

If you prefer to keep the visibility logic in Java (e.g. it grows more conditions), load the user's roles in one PK-prefix query and resolve in memory:

**1. Add a bulk projection to `UserCourseRoleRepository`:**
```java
// core/dto/UserCourseRoleEntry.java
public record UserCourseRoleEntry(long courseId, CourseRole role) {}

// UserCourseRoleRepository
@Query("SELECT new de.tum.cit.aet.artemis.core.dto.UserCourseRoleEntry(ucr.course.id, ucr.role) "
     + "FROM UserCourseRole ucr WHERE ucr.user.id = :userId")
List<UserCourseRoleEntry> findAllRolesByUserId(@Param("userId") long userId);
```

**2. Add a batch visibility method to `CourseVisibleService`** (keep the single-course one for genuine one-course callers):
```java
public Set<Course> filterVisibleCourses(User user, Collection<Course> courses) {
    if (authCheckService.isAdmin(user)) {            // admins see all (matches the old `|| isAdmin(user)`)
        return new HashSet<>(courses);
    }
    Map<Long, Set<CourseRole>> rolesByCourse = userCourseRoleRepository.findAllRolesByUserId(user.getId()).stream()
        .collect(Collectors.groupingBy(UserCourseRoleEntry::courseId,
            Collectors.mapping(UserCourseRoleEntry::role, Collectors.toCollection(() -> EnumSet.noneOf(CourseRole.class)))));
    ZonedDateTime now = ZonedDateTime.now();
    return courses.stream()
        .filter(c -> isVisibleInMemory(c, rolesByCourse.getOrDefault(c.getId(), Set.of()), now))
        .collect(Collectors.toSet());
}

private boolean isVisibleInMemory(Course course, Set<CourseRole> roles, ZonedDateTime now) {
    if (roles.contains(CourseRole.TEACHING_ASSISTANT) || roles.contains(CourseRole.EDITOR) || roles.contains(CourseRole.INSTRUCTOR)) {
        return true;                                  // at-least-TA: visible (already filtered to "not finished")
    }
    if (roles.contains(CourseRole.STUDENT)) {
        return course.getStartDate() == null || course.getStartDate().isBefore(now);
    }
    return false;
}
```

**3. Route the loops through it** — `CourseService.findAllActiveForUser` / `findAllActiveWithExercisesForUser`:
```java
return courseVisibleService.filterVisibleCourses(user, courseRepository.findAllActive(ZonedDateTime.now()));
```

This is **behaviour-preserving** (same admin / at-least-TA / student-after-start logic) and takes the dashboard from ~1000 queries to **1**. The same `Map<courseId, Set<CourseRole>>` pattern fixes **M5** (`findAllEnrollableForUser`) and the other per-course loops (`CourseService.findAllOnlineCoursesForPlatformForUser`, `CourseLearnerProfileResource`).

> **More aggressive variant** (also avoids materialising all 500 `Course` rows): push the membership into the query — `SELECT DISTINCT c FROM Course c JOIN UserCourseRole ucr ON ucr.course = c AND ucr.user.id = :userId WHERE <active> AND (ucr.role <> STUDENT OR c.startDate <= :now …)` — returns only the user's visible courses, with a separate "admins see all" path. Bigger change; the in-memory map above is the minimal, low-risk fix and is already proven at 0.17 ms.

## H2 — bulk enrollment (derived from measured per-query cost)

`UserService.addUserToCourse` fires ~6 reads + 1 UPDATE **per student, unconditionally** (existence `EXISTS` + reload-by-login + 3 `EXISTS` in `buildAuthorities` + `saveUser`), even for an already-enrolled student where develop short-circuited to **0 queries**. At the measured ~0.13 ms/round-trip, a **300-student CSV re-upload ≈ ~1800 reads + 300 UPDATEs ≈ 0.3–1.5 s** of pure DB round-trips (more on prod network), vs near-instant on develop. `ExamRegistrationService.registerStudentsForExam` adds one further `isInstructorInCourse` `EXISTS` per student.

## EXPLAIN highlights (index usage)

**S2 list users (PR)** — fully index-driven, no table scan:
```
-> Nested loop inner join (rows=2000)
   -> Covering index lookup on ucr using idx_ucr_course_role (course_id=1, course_role='STUDENT')
   -> Single-row covering index lookup on u using PRIMARY (id=ucr.user_id)
```

**S4 `existsByUser_IdAndRoleIn` (PR, no `course_id`)** — confirms the review's index-gap note: it seeks the **PK `user_id` prefix** then filters `course_role` (not a point lookup), but is cheap here because a user has few rows:
```
-> Limit: 1 row(s)
   -> Filter: (user_course_role.course_role = 'INSTRUCTOR')
      -> Covering index lookup on user_course_role using PRIMARY (user_id = 7)  rows=22
```

**S3 count GROUP BY (PR)** — improved overall (3.7× vs develop), though the optimizer drives from a `jhi_user` table scan (`deleted=0`) rather than `idx_ucr_course_role`; would scale with user count. Minor further-optimization opportunity, not a regression.

## Bottom line

- **The unfixed PR is a genuine, severe, common-path regression**, confirmed by average latency on the real stack: `/courses/for-dashboard` ~647 ms and `/courses/for-dropdown` ~322 ms at 524 active courses (vs ~23 / ~6.5 ms on develop), scaling linearly with course count — a hard merge blocker as-is.
- **The fix (`PR_12788_PERF_FIX.patch`) fully resolves it**: re-measured back-to-back under identical conditions, `PR + fix` equals develop on every endpoint (dashboard 22.2 vs 23.2 ms, dropdown 6.3 vs 6.5 ms, single-course 5.3 vs 5.0 ms) — i.e. **equal or faster**, and 25–100× faster than the unfixed PR. Recommend merging the PR only with this fix (or equivalent), and confirming `AuthorizationCheckServiceTest` passes.
- **H2 (bulk enrollment)** is the second real regression (lost short-circuit + per-user authority rebuild).
- Everything else the PR touches is **neutral or faster** — the `user_course_role` model is genuinely better for set-based queries (S3, S4); it's only the leftover *per-element loops* that need converting to bulk queries.
- Both regressions have the same fix shape: **replace the per-course / per-user loop with one set-based query**, which the new schema supports well (PK + `idx_ucr_course_role`).

---

### Reproduce

```bash
docker run -d --name ucr-bench-mysql -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=bench \
  -p 13306:3306 docker.io/library/mysql:9.7.0 \
  mysqld --lower_case_table_names=1 --character_set_server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
         --local-infile=1 --explicit_defaults_for_timestamp --max_connections=1000
python3 -m venv venv && ./venv/bin/pip install pymysql
./venv/bin/python bench.py          # harness: <scratchpad>/ucr-bench/bench.py
docker rm -f ucr-bench-mysql        # teardown
```
Harness script (`bench.py`) lives in the session scratchpad — ask me to copy it into the repo if you want to keep it.
