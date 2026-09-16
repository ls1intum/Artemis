# PR A1 Spec: Durable metadata sync via an outbox (System A)

Decision-complete brief for implementation. Read this together with
`weaviate-ingestion-hardening-plan.md` (the overall plan and the two-systems
framing). This spec is the source of truth for A1; if something here is ambiguous,
resolve it and update this file rather than guessing.

Status: not started. Do NOT commit or push without explicit approval.
Branch: `feature/global-search/searchable-entity-outbox` off `develop`.

---

## Goal

Make System A (the Artemis-owned `SearchableEntities` Weaviate collection) writes
durable. Today every metadata write is a fire-and-forget `@Async` call with
log-and-forget error handling in `SearchableEntityWeaviateService`, so a Weaviate
outage, node death, or exception loses the write with no retry (root cause P6).

A1 routes every write through a Postgres-backed outbox: the request records the
intent durably, and a background dispatcher performs the actual Weaviate write
with retry. The outcome (data lands in `SearchableEntities`) is the same as today;
the mechanism becomes durable and self-retrying.

## Non-goals (explicitly out of scope for A1)

- The DB->Weaviate reconcile / backfill of old or drifted entities. That is A2.
- Per-type DB loaders / re-read-at-dispatch. A1 uses payload snapshots (below);
  loaders belong to A2, which must walk the DB anyway.
- Any System B work (Iris/Pyris content pipelines).
- FAQ content ingestion into Iris `Faqs` (that is System B / optional B5). Note:
  FAQ metadata in `SearchableEntities` IS in scope here (it is just another type).
- Completeness metrics / admin dashboard (that is A2).

## Locked decisions

1. Storage = payload snapshot. Store the property map the call site already builds
   (`dto.toPropertyMap()`), not a re-read of the entity. No per-type loaders in A1.
2. No dedicated transaction wrapping. The enqueue is a plain repository save. It
   naturally joins an ambient transaction where one exists (many service-layer
   call sites), giving atomic enqueue there for free, and is a standalone commit
   where there is none (REST resources). A2's reconcile is the backstop for the
   tiny "committed entity but enqueue not yet committed" window. Do NOT add new
   `@Transactional` scope (CLAUDE.md convention).
3. Scope = all 9 entity types and all delete variants (see inventory below).
4. Idempotency via deterministic UUID keyed by `(type, entityId)`
   (`WeaviateUuidUtil.deterministicUuid`, already used at
   `SearchableEntityWeaviateService.java:557`). Re-applying an outbox row is safe.
5. Single serialized writer. The dispatcher runs on the scheduling node only
   (`@Profile(PROFILE_SCHEDULING)`), processes rows in id (enqueue) order, and
   serializes per entity so concurrent same-entity writes cannot race across the
   cluster. This is strictly more consistent than today's uncoordinated per-node
   `@Async` writes.
6. Conversion is centralized in `SearchableEntityWeaviateService`, NOT spread
   across the ~40 call sites (see "Conversion approach").

## Data model (Liquibase)

Add one changelog `config/liquibase/changelog/<timestamp>_changelog.xml` and
include it from `config/liquibase/master.xml` (same pattern as every other table).

Table `weaviate_outbox`:
- `id` bigint PK (generated)
- `operation` varchar not null (enum, see Operations)
- `entity_type` varchar null (the `SearchableEntitySchema.TypeValues` value, for
  UPSERT and DELETE_ENTITY)
- `entity_id` bigint null (for UPSERT and DELETE_ENTITY)
- `payload` text/jsonb null (serialized property map for UPSERT)
- `params` text/jsonb null (parameters for the bulk delete kinds, e.g.
  `{"courseId": 42}`)
- `attempts` int not null default 0
- `next_attempt_at` timestamp not null (default now; set to now + backoff on
  failure)
- `created_at` timestamp not null
- index on `(next_attempt_at)` and/or `(id)` to support the claim query

Table `searchable_entity_sync_state` (written by the dispatcher on success; read
by A2 later):
- `id` bigint PK
- `entity_type` varchar not null
- `entity_id` bigint not null
- `content_hash` varchar not null
- `synced_at` timestamp not null
- unique `(entity_type, entity_id)`

## Operations enum

The outbox must represent one UPSERT shape plus the delete variants found in the
call-site inventory:

- `UPSERT` -> `entity_type`, `entity_id`, `payload` (property map)
- `DELETE_ENTITY` -> `entity_type`, `entity_id`
- `DELETE_POSTS_FOR_CHANNEL` -> `params.channelId` (deletes POST + ANSWER_POST for
  the channel)
- `DELETE_POSTS_FOR_COURSE` -> `params.courseId` (deletes POST + ANSWER_POST for
  the course)
- `DELETE_ANSWER_POSTS_FOR_POST` -> `params.postId`
- `DELETE_ALL_FOR_COURSE` -> `params.courseId` (deletes ALL rows for the course)
- `DELETE_LECTURE_UNITS_FOR_LECTURE` -> `params.lectureId`

The dispatcher switches on `operation` and calls the matching internal write
method (below).

## Conversion approach (low-churn, centralized)

Do NOT edit the ~40 call sites. Instead, inside `SearchableEntityWeaviateService`:

1. Change each existing public method (`upsertCourseAsync`, `upsertLectureAsync`,
   `upsertLectureUnitAsync`, `upsertExamAsync`, `upsertExerciseAsync`,
   `updateExercisesAsync`, `upsertFaqAsync`, `upsertChannelAsync`,
   `upsertPostAsync`, `upsertAnswerPostAsync`, `deleteEntityAsync`,
   `deleteAllForCourseAsync`, `deleteAllLectureUnitsForLectureAsync`,
   `deleteAllAnswerPostsForPostAsync`, `deleteAllPostsForChannelAsync`,
   `deleteAllPostsForCourseAsync`) so its body ENQUEUES an outbox row instead of
   writing to Weaviate directly.
2. Make these methods synchronous (remove `@Async`). The enqueue is a tiny insert,
   and being synchronous is what lets it join the ambient transaction and preserve
   after-commit ordering. Method names keep the `Async` suffix for now to avoid
   touching call sites; the suffix is a slight misnomer (documented) and a rename
   is a trivial follow-up if desired.
3. Extract the current Weaviate-writing bodies into synchronous internal methods
   the dispatcher calls: reuse the existing `upsertRow(type, id, map)` and
   `deleteEntityInternal(type, id)`, and extract the inline filter logic of the
   `deleteAll*` methods into internal `doDelete...` methods keyed by the delete
   Operation.
4. `updateExercisesAsync(List, examId)` enqueues one `UPSERT` row per exercise.

Call sites therefore stay byte-for-byte unchanged; they keep calling the same
methods, which now enqueue. Enqueue only happens when the service bean exists,
i.e. when `WeaviateEnabled` is true (the service is already
`@Conditional(WeaviateEnabled.class)`), preserving today's feature gating.

### Full call-site inventory (reference; these files are NOT edited)

UPSERT:
- `course/web/CourseUpdateResource.java:205` course
- `admin/web/AdminCourseResource.java:193` course
- `lecture/web/LectureResource.java:522,525` lecture, lecture unit
- `lecture/service/LectureService.java:547,550` lecture, channel
- `lecture/web/AttachmentVideoUnitResource.java:195,294,359` lecture unit
- `lecture/web/OnlineUnitResource.java:143,193` lecture unit
- `lecture/web/TextUnitResource.java:135,181` lecture unit
- `communication/web/conversation/ChannelResource.java:571` channel
- `communication/service/conversation/ChannelService.java:77` channel
- `communication/service/ConversationMessagingService.java:486` post
- `communication/service/AnswerMessageService.java:464` answer post
- `communication/web/FaqResource.java:111,143` faq
- `exam/web/ExamResource.java:279,383,433` exam
- `exam/service/ExamImportService.java:185,186,246,247` exam + exercises
- `exam/service/ExamService.java:1601,1602,1617,1618` exam + exercises
- exercise version event: `SearchableEntityWeaviateService.onExerciseVersionCreated`

DELETE:
- `deleteEntityAsync`: `AnswerMessageService.java:309` (ANSWER_POST),
  `ChannelService.java:80,223,549` and `ChannelResource.java:574` (CHANNEL),
  `ConversationMessagingService.java:402` (POST), `FaqResource.java:186` and
  `CourseDeletionService.java:512` (FAQ), `ExamDeletionService.java:172` (EXAM),
  `ExerciseDeletionService.java:223` (EXERCISE), `LectureService.java:222,553`
  (LECTURE, CHANNEL), `LectureUnitResource.java:202`,
  `AttachmentVideoUnitResource.java:198,297,362`, `OnlineUnitResource.java:146,196`,
  `TextUnitResource.java:138,184`, `LectureResource.java:528` (LECTURE_UNIT)
- `deleteAllPostsForChannelAsync`: `ChannelService.java:224,548`,
  `ChannelResource.java:575`
- `deleteAllPostsForCourseAsync`: `ConversationDataCleanupService.java:56`
- `deleteAllAnswerPostsForPostAsync`: `ConversationMessagingService.java:403`
- `deleteAllForCourseAsync`: `CourseDeletionService.java:296`
- `deleteAllLectureUnitsForLectureAsync`: `LectureService.java:223`

## OutboxService + dispatcher

`WeaviateOutboxService` (or fold enqueue into `SearchableEntityWeaviateService`
and put the dispatcher in a new `WeaviateOutboxDispatcher`):
- `enqueueUpsert(type, entityId, propertyMap)`
- `enqueueDelete(operation, params)` / typed helpers per delete kind
- persistence via a Spring Data `WeaviateOutboxRepository`

`WeaviateOutboxDispatcher`:
- `@Conditional(WeaviateEnabled.class)`, `@Profile(PROFILE_SCHEDULING)`
- `@Scheduled(fixedRate = ...)` claim + drain, PLUS a trigger-on-enqueue nudge so
  freshness stays comparable to today's async write. If trigger-on-enqueue can
  fire on a non-scheduling node, route it to the scheduling node (cluster message)
  or rely on the scheduled tick; do NOT let arbitrary nodes dispatch concurrently.
- claim query: native `SELECT ... WHERE next_attempt_at <= now ORDER BY id ASC
  LIMIT :batch FOR UPDATE SKIP LOCKED` (idiom copied from
  `LectureUnitProcessingStateRepository.findIdleForDispatch`)
- per row: run the matching internal write on `SearchableEntityWeaviateService`;
  on success write/refresh `searchable_entity_sync_state` (content_hash from the
  payload) and delete the outbox row; on failure increment `attempts` and set
  `next_attempt_at = now + backoff` (exponential, cap attempts, keep the row for
  later; A2 reconcile is the ultimate backstop for poison rows)
- process rows in id order and serialize per entity so multiple pending rows for
  the same `(type, entityId)` apply latest-wins cleanly

## Concurrency guarantees to preserve/verify

- Source-of-truth DB writes are untouched by A1 (same JPA save / locking).
- Search reads are untouched (same Weaviate read path).
- Weaviate writes become single-writer + per-entity-ordered, which is at least as
  consistent as prod and strictly better under concurrent same-entity edits and
  update-vs-delete races for a SINGLE entity (same type + id). On a transient
  failure the dispatcher collapses superseded per-entity rows so a backed-off older
  row cannot overwrite a newer one (implemented as a delete keyed on
  `(entity_type, entity_id)`).

Implementation deviations (recorded per this file's "resolve ambiguity and update"
instruction):

- SKIP LOCKED dropped. The claim query is a plain read (`SELECT ... WHERE
  next_attempt_at <= now ORDER BY id ASC LIMIT :batch`), not `FOR UPDATE SKIP
  LOCKED`. Because the dispatcher is the only writer (`@Profile(PROFILE_SCHEDULING)`
  runs on exactly one instance, per the Artemis multi-node scheduling contract) and
  a `ReentrantLock` serializes drains on that node, there is no concurrent claimer to
  lock rows against. Dropping the lock also lets the Weaviate write run OUTSIDE any
  DB transaction, so a slow/hung write never holds a pooled connection (was a review
  finding). Crash-safety comes from the non-mutating read (a crashed row is simply
  re-read) plus idempotent apply, not from a lease.

Residual (bulk-delete vs per-entity race, NOT covered by the collapse):

- The collapse only keys on `(entity_type, entity_id)`, so bulk deletes
  (`DELETE_ALL_FOR_COURSE`, `DELETE_POSTS_FOR_CHANNEL`, etc., which have a null
  `entity_id`) neither collapse pending per-entity rows nor are collapsed by them.
  So a per-entity upsert deferred by backoff across a bulk delete can resurrect a
  deleted row (orphan), and a deferred bulk delete can wrongly erase newer rows.
  Narrow (needs a transient failure straddling a bulk delete). Left to A2, which
  must be EXTENDED to garbage-collect System A orphans (Weaviate rows whose DB
  entity is gone) -- A2 as originally specced only heals DB->Weaviate and would not
  detect such an orphan.

## Tests (JUnit, `*Test.java`, reuse module base classes)

- enqueue-on-change: each public method inserts the correct outbox row (right
  operation, type, id, payload/params) and does NOT write Weaviate synchronously.
- dispatcher happy path: drains an UPSERT row -> Weaviate write -> sync_state row
  written -> outbox row deleted.
- dispatcher retry: a failing Weaviate write leaves the row with incremented
  attempts and a future next_attempt_at; a later run succeeds.
- each delete Operation maps to the correct Weaviate deletion.
- idempotency: applying the same UPSERT row twice yields one row (deterministic
  UUID replace).
- ambient-transaction participation: when the caller's transaction rolls back, the
  outbox row is not persisted (enqueue joined the transaction).
- ordering: two UPSERT rows for the same entity apply latest-wins.

## Acceptance criteria

- No call site writes to Weaviate synchronously anymore; all go through the outbox.
- A Weaviate outage during a metadata change does not lose the write: the outbox
  row survives and the dispatcher applies it when Weaviate recovers.
- Feature gating unchanged: nothing is enqueued or written when `WeaviateEnabled`
  is off.
- Behavior with Weaviate up is functionally equivalent to today (data appears in
  `SearchableEntities`), with comparable freshness.
- `./gradlew spotlessCheck checkstyleMain` and the new tests pass. Run lint + the
  touched specs locally; CI runs the rest.

## Constraints

- Branch from `develop`; do not stack on another open PR.
- Follow Artemis Java conventions (records for DTOs, constructor injection, no
  wildcard imports, no new `@Transactional` scope, no `@Cache`).
- No em dashes in PR text. Use the Artemis PR template.
- Never commit or push without explicit approval.
