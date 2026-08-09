# Course overview performance follow-up plan

**Status:** Proposed follow-up work after the course-tab lazy-loading and exercise-projection PR.

**Last validated:** 2026-08-09.

**Scope:** The student dashboard after login, the common course shell, and every student course tab in the web app.

## Decision for the current PR

Do not add another broad production optimization to the current PR. The remaining opportunities cross REST contracts,
feature-module boundaries, or non-trivial business logic. Adding one now would make an already broad change harder to
review and roll back. Correctness fixes requested in review remain in scope, including preserving programming/quiz
actions in the projection, applying online-course LTI visibility to the title projection, and making the shared client
cache resilient to request and WebSocket races.

One small review-driven optimization is also included: returning to the lectures or exams tab during the same course
visit reuses its already loaded overview DTO response. The course-keyed state shares concurrent requests, rejects late
responses from a previous course, retries failed requests, and is cleared on an in-place course switch and when the
course container is destroyed. It neither persists across visits nor changes a REST contract. Only the
`exams-for-overview` response is reused; the two student-exam calls retain their existing refresh behavior.

The only additional performance change suitable for the current PR is test-only: payload and database-query budgets
for `exercises-for-overview`. They protect the main reduction without expanding runtime scope. The profile now checks
that required fields remain present, unused fields remain absent, Hibernate does not hydrate the exercise entity graph,
the 20-exercise text/programming projection stays at no more than eight statements, and its uncompressed JSON remains
below 20 KB. The latest validation produced 12,803 bytes and eight statements; a course with a visible quiz adds one
bounded batch-marker projection. The budgets deliberately leave payload headroom for legitimate small contract
additions.

## Goals

- Minimize time to the student dashboard and to the first useful rendering of a course tab.
- Transfer only fields used by the web client.
- Read only rows and columns needed by the server-side calculation or response.
- Keep query counts bounded as course content grows; remove per-item queries and large join-result matrices.
- Keep score and recommendation calculations stateless: explicit DTO input, explicit DTO output, no repository calls
  or wall-clock reads inside the calculation.
- Deliver the work as independently measurable and independently revertible pull requests.

## Non-goals

- Do not migrate native clients from the deprecated `for-dashboard` endpoint as part of these web optimizations.
- Do not replace a measured database problem with durable or cross-visit application caching. Artemis' normal
  projection, filtering, paging, and indexing rules apply. Per-visit request reuse is acceptable only for an identical
  response with an explicit course-switch and container-destruction eviction design.
- Do not combine unrelated tabs merely to reduce the displayed REST-call count. Calls should only be combined when
  their data has the same lifecycle and is needed by the same user action.
- Do not assert wall-clock timings in CI. Assert query/row/entity-load and payload budgets; log timings for comparison.

## Measurement protocol

The baseline below was measured through the real Spring REST handlers with PostgreSQL in Testcontainers and an
in-process web client. The representative course contained:

- 20 exercises with participation, submission, result, and representative problem statements;
- 20 lectures with attachment units;
- 20 competencies;
- 20 tutorial groups;
- 20 public channels with one message each;
- 20 accepted FAQs in five categories;
- one registered visible exam, a course grading scale, and generated learning-path data.

Each endpoint received three warm-up runs. Reported times are the median of five measured runs. Payloads are
uncompressed UTF-8 JSON. Query counts are Hibernate prepared-statement counts. Times exclude browser rendering,
network latency, TLS, compression, WebSocket traffic, analytics, images, and the application bootstrap.

The figures are comparative development-machine measurements, not production latency promises. Database row volume
must additionally be inspected from the projection/entity graph because statement count alone does not expose an
oversized join matrix.

## Baseline and triage

The common course shell is listed separately. Tab rows show the additional tab-specific work unless stated otherwise.

| Student action or tab                                   | REST calls | DB queries | Median server time | JSON bytes | Assessment                                                      |
| ------------------------------------------------------- | ---------: | ---------: | -----------------: | ---------: | --------------------------------------------------------------- |
| Dashboard after login                                   |          1 |          7 |            13.9 ms |     32,599 | High aggregate cost; broad entity graphs                        |
| Common course shell (`available-tabs` + `for-overview`) |          2 |          6 |             6.6 ms |        996 | Small response; duplicate request/auth overhead remains         |
| Exercises tab content                                   |          1 |        8–9 |            10.2 ms |     14,044 | Projection-only; the ninth query is conditional on quizzes      |
| Enter course on Exercises, including shell              |          3 |      14–15 |     about 15–17 ms |     15,040 | Acceptable result of this PR                                    |
| Lectures                                                |          1 |          2 |             2.7 ms |      2,688 | Narrow constructor projection                                   |
| Statistics, direct navigation                           |          2 |      16–17 |            15.1 ms |     14,107 | Reuses exercises but grade lookup is expensive                  |
| Statistics after Exercises is cached                    |          1 |          8 |             4.9 ms |         63 | Too much DB work for one displayed grade                        |
| Exams landing                                           |          3 |         10 |             9.2 ms |        208 | Too many calls for a tiny response                              |
| Competencies                                            |          1 |          5 |             4.3 ms |      3,572 | Good wire DTO; partial entity hydration remains                 |
| Communication landing, no selection                     |          3 |         36 |            28.9 ms |     17,380 | Per-channel N+1 in conversation conversion                      |
| Select one conversation                                 |         +2 |        +28 |           +22.5 ms |       +580 | Message/pinned paths repeat substantial setup                   |
| FAQ                                                     |          2 |          5 |             5.8 ms |      4,838 | Category data is transported twice                              |
| Tutorial groups                                         |          2 |          8 |             7.1 ms |      3,524 | Fetches all sessions for a summary view                         |
| Notification settings, first visit                      |          2 |          2 |             3.1 ms |      8,506 | Global metadata is static and DB-free; already cached per login |
| Calendar, first visit                                   |          2 |         12 |             8.7 ms |     10,964 | Requested months are filtered after broad DB loads              |
| Learning path, started path                             |          2 |        232 |            88.3 ms |         59 | Highest-priority per-request DB problem                         |
| Training leaderboard                                    |          1 |          7 |             5.7 ms |        340 | Repeated lookup and read-path initialization                    |
| Iris course chat, empty existing session                |          3 |         14 |            19.3 ms |        244 | Multiple setup calls; message history is unbounded              |

Conditional detail calls are deliberately absent from the totals: lecture auto-selection, exam conduction data,
remembered exercise/tutorial-group selections, a recommended learning object's detail, and optional Iris detail chat
can add requests. Each follow-up must measure both a clean first visit and the relevant selected-detail path.

Reopening lectures during the same course visit adds no second `lectures-for-overview` call. Reopening exams adds no
second `exams-for-overview` call, but still refreshes `test-exams-per-user` and `real-exams-sidebar-data`; consolidating
those calls requires a separate freshness and contract decision.

## Already satisfactory in this area

No follow-up should rewrite these paths without new evidence:

- `exercises-for-overview` uses constructor projections from database to server and a pure score calculator. Its
  profile forbids exercise, participation, submission, result, feedback, grading-scale, and plagiarism entity loads.
  One low-priority row-volume refinement remains: its shared grade-score queries select the student's rows for the
  course and discard rows for unreleased or LTI-hidden exercises in Java. This is tracked separately below because
  changing the shared projections in the current PR would widen its blast radius.
- `lectures-for-overview` is a narrow constructor projection containing the sidebar fields.
- `available-tabs` combines six indexed availability checks in one query. Iris stays separate because its effective
  default is Java-owned and its flag is stored in JSON.
- Notification metadata is static, costs no DB query, and is cached for the login session.
- The competency wire DTO is tailored and its progress lookup is bulk-oriented. Projecting the remaining hydrated
  competency data is lower priority unless a larger linked-competency fixture shows a material row or allocation cost.

## Follow-up PR 1: project the login dashboard

**Priority:** Highest system-wide impact. Every active student pays this cost before opening a course.

### Problem

`courses/for-dashboard` returns about 32.6 KB for one representative course and loads broad
course/exercise/participation/submission/result graphs. Seven statements look modest, but their selected columns and
join matrices are not. The web dashboard only renders course-card summaries.

### Design

1. Inventory every field read by the web dashboard course card, its active-exam banner, and notification indicators.
2. Introduce a web-specific `CourseDashboardCardDTO`; keep the old endpoint and its native-client contract unchanged.
3. Project scalar course-card and active-exam fields directly.
4. Project score inputs across all visible active courses in batches, then use a stateless calculator. Do not issue one
   score query per course.
5. Project counts and the next relevant exercise as aggregates/small rows instead of attaching exercise collections.
6. Switch only the web dashboard to the new endpoint.

### Acceptance criteria

- No Course, Exercise, Participation, Submission, Result, Feedback, or Exam entity hydration on the successful web
  dashboard path.
- Query count stays bounded when the fixture grows from one to 20 courses; there is no per-course query loop.
- At least a 60% payload reduction for the one-course fixture, with required-field and forbidden-field assertions.
- Dashboard cards, score display, notification counts, active exams, and navigation match the old endpoint in
  characterization tests.
- The old endpoint remains green for native clients and is not deprecated further in this PR.

## Follow-up PR 2: bulk-project learning-path navigation

**Priority:** Worst individual endpoint: 228 queries for a 14-byte navigation response in the measured fixture.

### Problem

The recommendation loop repeatedly resolves learning objects and completion state. Work grows with the number of
competencies and relations, even when no recommendation can be returned.

### Design

1. Add characterization tests comparing current recommendations for completed, partially completed, unavailable,
   cyclic/related, and empty learning paths.
2. Define one immutable recommendation-input graph: competency order/relations, available learning objects, progress,
   completion, learner-profile inputs, and the calculation time.
3. Load each input collection with bounded projection queries before entering the algorithm.
4. Move selection into a pure function that cannot access repositories or the clock.
5. Keep lecture/exercise detail loading outside the recommendation calculation and load only the selected object's
   existing detail endpoint.

### Acceptance criteria

- Recommendation equivalence for all characterization fixtures.
- At most 15 statements for both 20- and 100-competency fixtures; the count must not grow per competency or learning
  object.
- No repository invocation from inside the recommendation loop.
- No broad Exercise or Lecture graph hydration while deciding what to recommend.
- Preserve authorization, release dates, completion rules, competency ordering, and learner-profile behavior.

## Follow-up PR 3: remove the communication N+1

**Priority:** High; communication is frequently visited and the cost grows with channel count.

### Problem

Conversation conversion requests tutorial-group communication details separately for every channel. With 20 channels,
the conversation list costs 28 queries. Selecting a conversation then spends another 15 queries on the first message
page and 13 on an often-empty pinned-message request.

### Design

1. Add a single projection query for tutorial-group details keyed by all requested channel IDs.
2. Pass the resulting map into conversation DTO conversion; conversion itself must perform no repository calls.
3. Characterize public/private, archived, exercise, lecture, exam, tutorial-group, favorite, unread, and permission
   fields before changing the converter.
4. In a separate commit or PR, share authorization/context setup between normal and pinned messages.
5. Keep the normal message page as a bounded `Slice`. Load pinned messages on demand or return a bounded summary; do
   not merge an unbounded collection into the normal page.

### Acceptance criteria

- Conversation-list query count is independent of channel count (20 versus 100 channels differs by at most two
  statements) and is at most 10 for the 20-channel fixture.
- Conversion code contains no per-conversation repository/API call.
- First-page and pinned-message behavior, ordering, permissions, unread markers, and WebSocket updates remain intact.
- Pinned and Iris message-history endpoints have explicit bounds or pagination.

## Follow-up PR 4: make the common course shell a scalar contract

**Priority:** Medium aggregate impact; every course visit pays it, but its absolute server cost is already small.

### Problem

`CourseForOverviewDTO.course` still embeds a Course entity, the remaining course-overview DTO entity-field violation.
Projecting a second copy after loading the entity for authorization would only be cosmetic. The useful change is to
avoid hydrating it on the successful path and, if it remains simple, share work with tab availability.

### Design

1. Inventory the exact Course fields read by the overview container and cross-tab shared services.
2. Introduce a scalar `CourseOverviewShellDTO` containing those fields, notification count, and optionally the already
   computed tab flags.
3. Authorize the successful path by course ID/user role without first materializing Course. Keep the organization and
   prerequisite entity load only on the exceptional self-enrollment redirect path.
4. If tabs are included, make the guard and container share the same cached observable and migrate them atomically.

### Acceptance criteria

- Zero Course entity hydration on the normal enrolled-student path.
- Remove the `CourseForOverviewDTO.course` architecture-test ratchet entry.
- Preserve deep-link guard behavior, self-enrollment redirects, notification counts, date conversion, feature flags,
  and the race where tab data can arrive before shell data.
- Prefer one REST call and no more than four DB statements for the combined shell; do not combine them if it makes the
  route guard less reliable.

## Follow-up PR 5: remove the statistics grade-query overhead

**Priority:** Medium. The response is only 63 bytes but currently costs eight statements.

### Design constraints

- Do not add grading-scale/step data or a new query to every default Exercises visit merely to optimize the less common
  Statistics tab.
- Reuse the score already cached from `exercises-for-overview`.
- Either project the matching grade directly in a narrow endpoint or include it in the exercise response only if it can
  be derived from inputs that endpoint already loads at no additional DB cost.

### Acceptance criteria

- Statistics after Exercises needs at most one additional REST call and three DB statements; zero calls is preferred
  only if the Exercises path does not become more expensive.
- Grade names/bounds match the current service for percentage- and point-based scales, bonus grades, presentations,
  plagiarism adjustments, and absent grading scales.
- The response contains only the displayed grade information.

## Follow-up PR 6: filter calendar events in the database

**Priority:** Medium; current statement count is reasonable, but DB row transfer scales with the entire course history.

### Problem

The calendar endpoint loads course events from tutorial groups, exams, lectures, quizzes, and non-quiz exercises and
then filters the requested months in Java.

### Design

1. Define one shared interval-overlap rule, including null start/end dates and time-zone boundaries.
2. Add the overlap predicate to each event-source repository query.
3. Return calendar-event projections directly where practical.
4. Preserve the existing merge, de-duplication, and ordering after the bounded loads.

### Acceptance criteria

- Returned events are identical at month boundaries and across DST transitions.
- Rows read scale with events overlapping the requested range, not with all historic course events.
- No increase above the current 11 event-data statements without a measured row-volume justification.
- Test a multi-year course where only one month is requested.

## Follow-up PR 7: project tutorial-group summaries

**Priority:** Medium-low.

### Problem

The overview returns a good summary DTO but obtains it from an entity query that fetches teaching assistants,
registrations, all sessions, schedules, and channels. The tab needs the next session and small attendance/registration
summaries, not the complete session history.

### Design and acceptance criteria

- Project the scalar group summary, batch the current student's registration/attendance data, and select next sessions
  in the database.
- Do not fetch all sessions or multiply groups by registrations/sessions in one join result.
- Keep query count bounded for 20 and 100 groups and prove a smaller row matrix with Hibernate statistics or SQL
  inspection.
- Reuse the existing lecture overview projection/cache for the separate tutorial-lecture request.

## Follow-up PR 8: consolidate small tab contracts

These are independent cleanup PRs and should not be bundled together unless they share a client lifecycle.

### Exams

- Return visible exam summaries, test-attempt summaries, and real-exam sidebar status in one composite overview DTO.
- Replace the remaining `StudentExam` entity response with the existing DTO pattern.
- Target one landing call and at most five statements for the representative course.

### FAQ

- Derive the student filter categories from the accepted FAQ DTOs when semantics match, or return both in one response.
- Verify whether empty categories are intentionally shown before removing the separate category query.
- Avoid loading the same FAQ data independently in Communication and the FAQ tab within one course visit.

### Training

- Remove repeated repository lookup in leaderboard assembly.
- Make the GET path read-only; initialize a leaderboard preference only on an explicit write or account setup path.
- Preserve opt-in privacy behavior and leaderboard ordering.

## Follow-up PR 9: bound and simplify Iris course chat startup

**Priority:** Separate Iris-owned change because consent, settings defaults, session creation, and WebSocket behavior need
specialized tests.

- Avoid loading status, current session, and session overview through three independent setup paths when the same
  authorization/settings data can be shared safely.
- Do not create a session merely by opening a read-only overview unless product semantics require it.
- Page session messages and initially load the newest bounded slice; support loading older messages explicitly.
- Test enabled/disabled Iris, missing/default settings, consent required/granted, empty/new/existing sessions, and live
  WebSocket updates.

## Follow-up PR 10: restrict overview score rows to visible exercises

**Priority:** Low until a course with many hidden exercises shows material row volume; the current query count and
entity-hydration profile are already bounded.

### Problem

`exercises-for-overview` reuses three grade-score projections that are keyed by course. The service then removes rows
whose exercise is unreleased or, for an online course, has not been launched by the requesting student. The returned
DTO and score are correct, but the database can still send score rows that the server immediately discards.

### Design and acceptance criteria

- Add overview-specific grade-score projections keyed by the already computed visible exercise IDs, or safely extend
  the shared projections only after characterizing their other consumers.
- Preserve individual quiz, individual non-quiz, team, rated-result, individual-due-date, and practice-mode semantics.
- Assert score equivalence with the current DTO calculator for released, unreleased, and LTI-hidden exercises.
- With 20 visible and 100 hidden exercises, selected grade rows should match the visible set and query count must not
  grow. Do not combine the three subtype queries unless measurement shows a clear benefit and the SQL stays readable.

## Delivery order

The recommended order balances total student impact, measured severity, and isolation:

1. Project the login dashboard.
2. Bulk-project learning-path navigation.
3. Remove the communication N+1, then bound message histories.
4. Make the common shell scalar and remove the grade-query overhead as separate PRs.
5. Push calendar filtering into the DB and project tutorial-group summaries.
6. Consolidate Exams, FAQ, and Training contracts in independent small PRs.
7. Handle Iris startup and history paging in an Iris-owned PR.
8. Restrict overview grade-score rows when row-volume measurements justify the shared-query change.

Dashboard, learning path, and communication are independent and can be developed in parallel, but each should be
reviewed and measured separately.

## Definition of done for every follow-up

1. Trace every response field to a web consumer; assert required fields are present and known unused heavy fields are
   absent.
2. Capture behavior with characterization tests before replacing entity-based logic.
3. Measure before and after in the same JVM/database fixture with warm-up and median runs.
4. Record REST calls, SQL statement count, payload bytes, hydrated entity counts, and whether result rows grow with
   content size.
5. Add a scaling assertion comparing the representative fixture with a larger one; a query inside a loop is a failure
   even when the 20-item timing looks fast.
6. Keep calculations pure and pass one captured `calculationTime` through the input DTO when time affects behavior.
7. Test authorization and exceptional paths separately; optimizing the enrolled-student path must not weaken access
   control or self-enrollment behavior.
8. Run focused server/client tests, architecture tests, formatting/linting, type checking, and the relevant manual or
   E2E workflow.
9. Put measured before/after values and contract changes in that PR's description.
