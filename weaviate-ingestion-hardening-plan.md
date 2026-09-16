# Weaviate Ingestion Hardening Plan

Status: draft for review. No code written, no tickets created, nothing committed.
Author context: consolidates the end-to-end study of both Weaviate ingestion
pipelines across Artemis and edutelligence/iris. Every "do X" below was checked
against the actual code and client versions so the plan does not fall apart
mid-implementation. Feasibility notes cite what was verified.

---

## 0. Framing: there are two independent Weaviate pipelines

Almost all "some data is there, some isn't" confusion comes from treating these
as one. They are separate instances, separate collections, separate owners, and
separate reliability models.

| | System A: Artemis metadata search | System B: Iris/Pyris content |
|---|---|---|
| Instance | Artemis-owned | Pyris-owned |
| Collections | `SearchableEntities` (one collection, all types) | `Lectures` (page chunks), `LectureUnits`, `LectureTranscriptions`, `LectureUnitSegments`, `Faqs` |
| Stores | Metadata rows: Course, Lecture, LectureUnit, Exam, Exercise, FAQ, Channel, Post, AnswerPost | Embedded content: slide chunks, transcription segments, unit + segment summaries, FAQ content |
| Written by | `SearchableEntityWeaviateService` (Artemis, directly) | Pyris pipelines, triggered by Artemis webhooks |
| Reliability today | Fire-and-forget `@Async`, best-effort, no retry/state/reconcile | Durable DB-backed state machine (`lecture_unit_processing_state`) with retry, stuck-detection, Iris-restart recovery, legacy backfill |

Guiding principle (this is also Weaviate's own recommendation, confirmed via
their docs): Postgres is the transactional source of truth; Weaviate is a
derived index we continuously converge toward Postgres. We never rely on
Weaviate for atomicity it does not have (it has no transactions; the only atomic
unit is a single-object write; batch is not all-or-nothing). We reduce every
"commit" to a single-object write, verify writes against reality instead of
trusting acknowledgements, and make everything idempotent so retries and
reconciles are always safe.

Order chosen: System A first (lower risk, single repo, no wire contract, fixes
the "old courses invisible in search" front-door problem), then System B.

---

## SYSTEM A (do first)

Two PRs, both in Artemis. No cross-repo work, no Pyris wire contract, no #715
coupling.

Scope note: FAQ appears in both systems. Its metadata row in `SearchableEntities`
is System A and is fixed automatically by A1/A2 (it is just another entity type,
`upsertFaqAsync`). FAQ content ingestion into Iris's `Faqs` collection is System B
and is handled by B1 and optional B5, not here.

### PR A1 - Durable metadata sync via an outbox

Goal: a metadata change is never lost, even if Weaviate is down or the node dies.

What it changes:
- New `weaviate_outbox` table via a Liquibase changelog (same mechanism that
  created `lecture_unit_processing_state`; changelogs live in
  `src/main/resources/config/liquibase/changelog/<timestamp>_changelog.xml` and
  are included from `master.xml`). Columns: id, entity_type, entity_id,
  operation (UPSERT/DELETE), payload snapshot (property map) + content_hash,
  created_at, attempts, next_attempt_at, status.
- New `searchable_entity_sync_state` table (type, entity_id, content_hash,
  synced_at). The dispatcher writes it on a confirmed Weaviate write. A2 reads it.
- Replace the fire-and-forget `@Async` calls in `SearchableEntityWeaviateService`
  (`upsert*Async`, `delete*Async`) with an outbox enqueue at the existing call
  sites (`CourseUpdateResource`, `LectureResource`, `LectureService`,
  `AttachmentVideoUnitResource`, `OnlineUnitResource`, `TextUnitResource`,
  `ChannelResource`, `ChannelService`, `AdminCourseResource`, the exercise
  version event listener, etc.).
- A scheduled dispatcher drains the outbox with `FOR UPDATE SKIP LOCKED`
  (identical idiom to `LectureUnitProcessingStateRepository.findIdleForDispatch`),
  retries with backoff, and deletes the row only after a confirmed write.
  Gate with `@Conditional(WeaviateEnabled.class)` and `@Profile(PROFILE_SCHEDULING)`
  so it runs on one node only.

Feasibility (verified):
- SKIP LOCKED native-query pattern already exists and is reusable.
- Liquibase changelog + master.xml include pattern confirmed.
- Deterministic per-entity UUIDs already exist (`WeaviateUuidUtil`,
  `SearchableEntityWeaviateService.java:557`), so the dispatcher's write stays
  idempotent (Weaviate replaces by UUID).

Important design decision (verified): do NOT bother with a same-transaction
outbox. Use a simple durable enqueue-after-save and lean on A2's reconcile as the
correctness guarantee.

- The outbox itself is the real win (durability + retry). Strict atomicity
  between the entity write and the enqueue is close to redundant here, because
  A2's reconcile is mandatory anyway (old/pre-feature courses, drift, never-indexed
  entities) and already catches everything a transaction would protect, plus more.
- What a same-tx outbox would buy: closing the microsecond window where a node
  dies exactly between the entity commit and the enqueue commit. Consequence if
  ever hit: one entity is briefly missing from search until the next reconcile
  pass, then self-heals. Low-value insurance against a rare, self-healing event.
- What it would cost: `TransactionTemplate` boilerplate co-located at ~15 call
  sites. (For the record: a transaction here is allowed. CLAUDE.md's "avoid
  `@Transactional` scope" is about breadth, not a ban; `@Transactional` is used in
  ~79 files and `TransactionTemplate` / `PlatformTransactionManager` are already
  used in `AnswerMessageService`, `IrisLectureUnitSyncService`,
  `SlideSplitterService`. We are choosing not to, on cost/benefit, not because it
  is disallowed.)
- Chosen shape: `entityRepo.save(...)` then `outboxRepo.save(row)` (each its own
  auto-transaction), enqueue strictly after save. Store `{type, id, op}` in the
  outbox row and have the dispatcher RE-READ the current entity from the DB at
  dispatch time (Postgres is the source of truth; `ExerciseSearchableEntityLoadService`
  already establishes this loader pattern). Re-reading makes duplicate/racy
  enqueues harmless (always writes current truth, idempotent by deterministic
  UUID) and removes payload-staleness and ordering concerns, which is most of what
  a transaction would have cleaned up.
- Revisit a transaction only if the reconcile were removed (it will not be) or a
  tight metadata-freshness SLA appeared (search metadata is best-effort-fresh).

Problems fixed: P6 (lost metadata writes going forward). Partially sets up P9/P12.
Size: medium. Depends on: nothing.

### PR A2 - Metadata reconcile + observability

Goal: old/never-indexed courses appear, and any drift self-heals, forever.

What it changes:
- A scheduled reconciler (`@Profile(PROFILE_SCHEDULING)`) walks each entity type
  in the DB in bounded, paged batches (oldest-synced-first), computes the current
  content_hash, and compares to `searchable_entity_sync_state`. Missing (no ledger
  row = never indexed, including pre-feature old courses) or drifted (hash
  mismatch) enqueues to the A1 outbox. Idempotent and batched, so it scales to
  thousands of courses.
- Per-type and per-course completeness metrics (DB count vs synced count), an
  admin view, and drift alerting.
- Put the bulk backfill behind a feature toggle so it is turned on deliberately
  (it generates real Weaviate load).
- System A orphan GC (REQUIRED, added after A1 review). The reconcile above only
  heals DB->Weaviate (missing/drifted). It must ALSO detect and delete Weaviate rows
  in `SearchableEntities` whose backing DB entity no longer exists. A1's per-entity
  collapse does not cover bulk-delete-vs-per-entity races (a backed-off upsert can
  resurrect a row a bulk delete removed), so without this GC such orphans persist
  indefinitely. Walk `SearchableEntities` (or the ledger) per type and delete rows
  with no live DB entity; keep it batched/idempotent like the rest of the reconcile.

Feasibility (verified): paged Spring Data queries and the SKIP LOCKED idiom
already exist; the reconciler only reads Postgres + the ledger and enqueues, so
it never blocks and never depends on Weaviate being up.

Problems fixed: P9 (old/pre-feature courses never indexed), P12 for System A
(drift after write), P16 for System A (no completeness visibility). Also closes
the residual A1 gap (save-then-crash-before-enqueue).
Size: medium. Depends on: A1 (reuses outbox + ledger).

---

## SYSTEM B (after System A)

Four core PRs (B1-B4) plus one optional follow-on (B5). Three live in
edutelligence/iris, one in Artemis, and the FAQ follow-on is mixed.

### PR B1 (iris) - Fail ingestion on partial batch writes

Goal: a partial write becomes a FAILED job instead of a silent success.

What it changes: after every batch block, inspect `collection.batch.failed_objects`
(and/or `batch.number_errors`) and raise if non-empty, so the pipeline's existing
try/except reports failure. Apply to every batch writer: page ingestion
(`lecture_ingestion_pipeline.py:372-384`), transcription ingestion
(`transcription_ingestion_pipeline.py:144-151`), and the FAQ ingestion pipeline.

Feasibility (verified): `failed_objects` is a property on the batch wrapper
available after the context manager exits (confirmed in the installed
weaviate-client 4.20.5 and in Weaviate's own docs, which state plainly that a
successful request does not mean all objects were ingested). This is a small,
low-risk change with an immediate correctness payoff.

Problems fixed: P2 (silent partial batch success), for lectures, transcriptions,
and FAQ content.
Size: small. Depends on: nothing. Ship first.

### PR B2 (iris) - Atomic, generation-based re-ingest

Goal: a re-ingest either fully replaces a unit's content or leaves the previous
complete version intact. No holes, ever. Replaces the destructive delete-first
pattern.

What it changes:
- Add a `generation` property (the per-dispatch job token, which is already fresh
  per dispatch) to every content object in `Lectures`, `LectureTranscriptions`,
  and `LectureUnitSegments`. Make object IDs deterministic and generation-scoped
  so a new generation never overwrites the old one.
- Write new-generation objects first (never delete first). Verify:
  `failed_objects` empty AND object count == expected. Only then commit by
  writing/flipping `committed_generation` on the single `LectureUnits` row.
  That single-object write is the atomic commit, and it is already the last step
  of the pipeline (`lecture_unit_pipeline.py:114-115`), so it maps onto existing
  code. One pointer commits pages + transcriptions + segments together.
- Make retrieval filter content by `generation == committed_generation`. Because
  Weaviate has no joins and the retrievers query content collections directly
  (confirmed: `lecture_page_chunk_retrieval.py:218`,
  `lecture_transcription_retrieval.py:195`,
  `lecture_unit_segment_retrieval.py:194`), this is a two-step read: fetch the
  unit row's `committed_generation` (a cheap single-object fetch, which some
  retrievers already do at `lecture_unit_collection.query.fetch_objects`), then
  add `generation == X` to the content query.
- Monotonic, owner-gated commit: only accept a generation newer than the current
  pointer, and only if the run still owns the unit. This composes with #715's
  supersede/ownership model so a superseded old run cannot commit stale data.
- Sweep (proactive at run start + inline after commit): delete this unit's
  non-committed generations.

Feasibility (verified):
- Schemas are mutable via the existing `_add_property_if_missing` /
  `collection.config.add_property` idempotent-migration pattern
  (`lecture_unit_page_chunk_schema.py:33-81`), so adding `generation` /
  `committed_generation` is straightforward and backward compatible.
- The unit row is already written last, so it is a natural commit point.
- The two-step read touches exactly three retrievers, all of which follow the
  same direct-query pattern; the change is bounded and well-defined.

Why this PR is irreducibly large: the write side and the read-filter side must
land together, or reads serve mixed generations. It cannot be safely subdivided.

Lighter fallback (documented, if B2 must shrink): version-in-id + insert-new +
delete-old-versions + GC, with NO read-path filter. This removes the hole (the
worst symptom) without touching retrievers, at the cost of a brief window where
reads may see duplicate/overlapping content across versions, and stale reads if a
delete fails until GC runs. Weaker guarantee, smaller PR. Recommended only if the
read-path change is deemed too risky for one PR.

Problems fixed: P3 (destructive non-atomic writes / holes), the atomicity half of
P1, and P14 partially (proactive + inline sweep of orphans).
Size: large (irreducible). Depends on: B1. Coordinate with / land after #715.

### PR B3 (iris) - Ingestion audit, counts, and orphan GC

Goal: make ingestion observable and self-cleaning, and give Artemis a reality
signal.

What it changes:
- Add per-collection object counts (pages, transcription segments, summaries) to
  the terminal `finish()` payload as an optional, additive field on
  `IngestionStatusUpdateDTO` (same pattern as the existing optional
  `display_page_numbers`).
- Add `GET /lectures/{unitId}/ingestion-audit` returning actual per-collection
  counts for a unit (a cheap Weaviate aggregate).
- Add a periodic GC sweep that deletes non-committed generations older than one
  ingestion timeout (the backstop for crash/Artemis-down orphans). Hook it into
  the existing `BackgroundScheduler` in `main.py:39`.

Feasibility (verified):
- Additive optional DTO field is a proven, wire-safe pattern (precedent:
  `display_page_numbers` on `IngestionStatusUpdateDTO`).
- Pyris already instantiates an APScheduler `BackgroundScheduler`, so the sweep
  has a home. `delete_many` by filter is idempotent, so if Pyris runs multiple
  workers the sweep is safe (at worst redundant).

Problems fixed: enables the fix for P1/P15 (reality signal), completes P14 (GC
backstop for leftover data).
Size: small-medium. Depends on: B2 (needs generations to exist).

### PR B4 (Artemis) - Verify against reality + extend the reconciler

Goal: DONE means Weaviate actually contains what it should; heal lost callbacks,
old courses, and the throughput ceiling. This extends the existing System B
scheduler; it does not rebuild it.

What it changes:
- Consume the counts from the terminal payload (add optional fields to
  `PyrisLectureIngestionStatusUpdateDTO`, which already carries optional additive
  fields), store expected vs actual on `lecture_unit_processing_state` (new
  columns via Liquibase), and gate the DONE transition on a match instead of
  trusting `success` alone (`ProcessingStateCallbackService.handleIngestionComplete`,
  line 233).
- Add a reality-reconcile pass to `LectureContentProcessingScheduler` that
  periodically compares DONE units' expected counts against the B3
  `ingestion-audit` endpoint and re-enqueues mismatches. Before re-running a
  stuck/failed job's full pipeline, check the audit so an already-present unit
  heals cheaply instead of re-transcribing.
- Widen backfill scope beyond active courses
  (`AttachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses`, line 127)
  behind a config knob.
- Make `MAX_CONCURRENT_PROCESSING` (currently a constant,
  `ProcessingStateCallbackService.java:65`) a configurable `@Value` so backlog
  drain is controllable.
- Surface the verified status to instructors (the status endpoint
  `LectureUnitResource.getUnitStatuses`, line 246, currently reads only the DB
  state) and add per-course completeness metrics.

Feasibility (verified):
- `PyrisConnectorService` already makes REST GET calls to Pyris
  (`getFaqIngestionState`, line 420), so calling the audit endpoint is the same
  pattern.
- The scheduler and DB-backed queue already exist and are reused wholesale.
- Optional-additive wire fields are proven both directions.

Problems fixed: correctness half of P1, P7 (lost callback heals via reconcile),
P8 (redo avoided by checking reality first), P10 (backfill scope), P11
(throughput), P12 for System B (drift after DONE), P15 (instructor status now
truthful), P16 for System B (metrics).
Size: medium. Depends on: B3 deployed first (B3 stays backward compatible, so
Artemis without B4 keeps working).

### PR B5 (iris + Artemis, OPTIONAL follow-on) - FAQ content durable-state parity

Goal: bring FAQ content ingestion (Iris `Faqs` collection) under the same durable
state model as lectures.

What it changes: give FAQ ingestion an Artemis-side durable processing state with
retry/reconcile (today it is fire-and-forget via `PyrisWebhookService.addFaq`, and
its state is only ever live-queried from Pyris via
`PyrisConnectorService.getFaqIngestionState`, line 420). Reuse the outbox/state
patterns from A1 and B4.

Problems fixed: P13 (FAQ content has no durable state/retry/reconcile).
Size: small-medium. Depends on: B4 patterns. Deferrable.

Note: FAQ's `failed_objects` check is already covered by B1, and FAQ metadata in
`SearchableEntities` is already covered by A1/A2. B5 only adds the durable-state
machine for FAQ content.

---

## Sequencing and parallelism

```
System A:   A1 (outbox) --> A2 (reconcile + observability)          [Artemis]

System B:   B1 --> B2 --> B3 --(wire contract)--> B4                [iris x3, Artemis x1]
                                                    |
                                                    +--> B5 (optional)
```

- System A first, as chosen. A1 and A2 are independent of System B and can start
  immediately.
- Within System B, B1 ships first (small, stops new holes). B2 is the big one.
  B3 must ship before B4 relies on it, and B3 is backward compatible so nothing
  breaks in the gap.
- If two people are available, one can run System A (A1 -> A2) while the other
  runs System B (B1 -> B2 -> B3 -> B4) in parallel; they never touch the same
  code.
- All behavior-changing pieces (reconcilers, reality-gating, backfill) sit behind
  existing feature toggles (`LectureContentProcessing`, `WeaviateEnabled`) for
  safe, staged rollout.

Minimum-count option: fold B1 into B2 to make System B three PRs, but this
enlarges the already-large B2 and loses the day-one fix. Not recommended.

---

## Master problem list (inclusive)

Every ingestion problem found in the study, across both systems.

Correctness / data integrity
- P1. "DONE" trusts the writer's acknowledgement, never verified against Weaviate
  reality. Acute in System B (trusts Pyris `finish()`), also true of System A
  (trusts the async call returned).
- P2. Weaviate batch results are not checked (`failed_objects`), so a batch where
  some objects fail exits cleanly and the job reports success with partial data.
- P3. Destructive delete-then-insert (non-atomic) in the page, transcription, and
  unit-summary pipelines: a crash between delete and completed insert leaves a
  holed or empty unit, and every retry repeats the destruction.
- P4. Per-slide vision/merge failures are swallowed and return empty, so slides
  can silently lose content without failing the job. Quality-level, not a hole.
- P5. Overlapping same-unit runs could drop follow-ups or let an old run
  overwrite newer data or send a terminal `finish()`. (Being fixed by #715.)

Durability / lost work
- P6. System A metadata writes are fire-and-forget `@Async` with log-and-forget,
  so a Weaviate outage, node death, or exception loses the write with no retry.
- P7. Iris to Artemis status callback retries only 3 times over ~30s, then gives
  up to Sentry; if Artemis is down longer, the terminal result is lost.
- P8. Artemis-down-while-Iris-works, or Iris-down: the job is lost from Artemis's
  view; the existing recovery re-runs the full expensive pipeline instead of
  checking whether the data is already present.

Completeness / staleness / coverage
- P9. No DB-to-Weaviate reconcile for System A: old/pre-feature courses and any
  failed async write never appear in search.
- P10. System B backfill is scoped to active courses only, so old/archived
  courses are never ingested.
- P11. System B throughput is capped at MAX_CONCURRENT_PROCESSING = 2 cluster-wide,
  so draining thousands of legacy units is glacial.
- P12. Drift after a successful write (Weaviate data later lost via backup
  restore, collection recreate, or a raced delete) is never detected in either
  system, because nothing re-checks reality after DONE.
- P13. FAQ content ingestion (Iris `Faqs`) is fire-and-forget with no durable
  state, retry, or reconcile; its state is only live-queried from Pyris.
- P14. Leftover half-written / orphaned data (from crashes or superseded
  generations) has no garbage collection. Exposed by the atomic redesign and
  must be handled as part of it.

Truthfulness / observability
- P15. Instructor-facing status is derived purely from the DB processing state,
  never from reality, so it can show DONE while Weaviate is empty or partial.
- P16. No completeness metrics, no way to measure "is everything ingested," no
  drift alerting, for either system.

---

## Problem-to-PR matrix

| Problem | System | Fixed by | Notes |
|---|---|---|---|
| P1 trust-the-callback | A + B | B3 (reality signal) + B4 (gate DONE); A2 for System A drift | Core correctness fix |
| P2 unchecked batch failures | B | B1 | Covers lectures, transcriptions, FAQ |
| P3 destructive delete-then-insert | B | B2 | Insert-then-commit-then-sweep |
| P4 swallowed per-slide vision failures | B | Partially B2/B4 | Residual: counts match even when a slide's description is empty; true fix is a quality threshold / vision retry, tracked as follow-up |
| P5 overlapping same-unit runs | B | #715 (external) | B2 composes via monotonic owner-gated commit |
| P6 lost metadata writes | A | A1 | Durable outbox |
| P7 lost status callback | B | B4 (+ B2/B3) | Reconcile heals; audit lets it heal cheaply |
| P8 lost job / wasteful redo | B | B4 | Check reality before re-running |
| P9 old courses never indexed (metadata) | A | A2 | Reconcile backfills missing entities |
| P10 backfill scoped to active courses | B | B4 | Widen scope behind config |
| P11 throughput cap of 2 | B | B4 | Configurable concurrency |
| P12 drift after success | A + B | A2 (System A) + B4 (System B) | Reality reconcile in both |
| P13 FAQ content no durable state | B | B5 (optional) | B1 covers its batch check; A1/A2 cover FAQ metadata |
| P14 orphaned half-written data | B | B2 (proactive+inline sweep) + B3 (GC backstop) | The direct answer to leftover data |
| P15 instructor status not truthful | B | B4 (+ B3) | Status from verified reality |
| P16 no completeness metrics/alerting | A + B | A2 (System A) + B4 (System B) | Observability |

Residual not fully closed by this plan: P4 (silent per-slide quality
degradation). Everything else maps to a shipping PR. FAQ content (P13) is closed
only if the optional B5 is done; without B5, FAQ content keeps its current
fire-and-forget behavior (but its metadata and batch-failure check are still
fixed by A1/A2/B1).

Future hardening (System A, not in A1): the outbox dispatcher is single-writer, so
while Weaviate is unresponsive a hung write blocks the drain for up to the client's
timeout (client6 defaults: init 30s, query 60s, insert 120s), and a backlog costs one
timeout per row. Durability is never affected (rows stay in the outbox) and the drain
self-recovers, so this is a bounded liveness/freshness issue, not a correctness one.
A small circuit breaker around the Weaviate write (open after N consecutive failures,
fast-fail during a cooldown, half-open probe to recover) would fast-fail during an
outage instead of paying a timeout per row. Optionally pair it with a tighter insert
timeout. Fits naturally with the A2 observability work; deliberately left out of A1.

---

## Cross-cutting constraints to honor (from the global-search workflow)

- Pyris wire contract (B3, B4, B5): new fields additive and optional, nothing
  renamed, old-Iris compatibility proven in `WireFormatContractTest`.
- i18n (A2, B4): any new status/error/admin keys in both `en/*.json` and
  `de/*.json`, specs in the same commit.
- Each PR branches from `develop`; no stacking except where the dependency arrows
  force it (A2 on A1, B2 on B1, B3 on B2, B4 on B3, B5 on B4), and those rebase
  forward as the parent merges.
- Local checks: lint plus the single touched spec; CI runs the rest. No em dashes
  in PR text. Artemis PR checklist template applies.
- Never commit or push without explicit approval.
