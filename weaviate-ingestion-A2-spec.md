# PR A2 Spec: Metadata reconcile, backfill, and orphan GC (System A)

Decision-complete brief for implementation. Read together with
`weaviate-ingestion-hardening-plan.md` (overall plan, two-systems framing) and
`weaviate-ingestion-A1-spec.md` (the outbox this builds on). This file is the
source of truth for A2; resolve ambiguity here and update it rather than guessing.

Status: planning. No code written. Do NOT commit or push without explicit approval.
Branch: stacked on `feature/global-search/searchable-entity-outbox`.

---

## 0. Ground truth (verified in the tree, not from the A1 spec)

A1 as merged into the branch differs from its own spec in ways that make A2 much
smaller. Verified by reading the branch, not by trusting the spec:

- The outbox stores IDENTITY ONLY (`operation`, `entity_type`, `entity_id`,
  `params`). It does NOT store a payload snapshot, contrary to A1 spec locked
  decision 1.
- `SearchableEntityResolver.resolve(type, entityId)` re-derives the current
  property map from Postgres at dispatch time, for all 9 entity types. This is
  the "DB loaders" work the original A2 plan called for. It already exists.
- `searchable_entity_sync_state` (`entity_type`, `entity_id`, `content_hash`,
  `synced_at`) is refreshed on every confirmed upsert and deleted when an entry
  resolves to a delete.
- `content_hash` = SHA-256 of the canonical (key-sorted) JSON of the re-derived
  property map. `source_seq` is deliberately excluded so the hash is a stable
  drift signal. Computed in `SearchableEntityWeaviateService` via private
  `sha256Hex(serializeMap(...))`.
- `SearchableEntitySchema.Properties.SOURCE_SEQ` is stamped on every written row
  and bulk deletes are fenced by `writtenBefore(outboxId)`.
- The dispatcher is single-writer on `PROFILE_SCHEDULING`, plain read (no
  `FOR UPDATE SKIP LOCKED`), retry with exponential backoff, per-entity collapse,
  drains in `id ASC` order.
- `toPropertyMap()` OMITS null optional fields, so the hash covers only keys
  actually present.
- `WeaviateService.addMissingProperties` runs at startup and adds any schema
  property missing from an existing collection. Adding a new property to
  `SearchableEntitySchema` is therefore cheap and needs no data migration. This
  is how `source_seq` landed.
- `CollectionHandle.paginate()` (weaviate client6 6.2.1) gives a cursor-backed
  `Paginator` with `pageSize`, `filters`, `returnProperties`, and `fromCursor`.
  That is a resumable, cap-free full scan, which the 10,000-row
  `QUERY_MAXIMUM_RESULTS` offset cap otherwise blocks.

### Conventions verified

- Configuration: Artemis uses `@Validated @ConfigurationProperties(prefix = ...,
  ignoreUnknownFields = false)` records with `@DefaultValue` and jakarta
  validation, registered by a small `@Configuration @EnableConfigurationProperties`
  class. 16 such classes exist. ZERO uses of `@Value("${artemis...}")` exist.
  Template: `DataCleanupProperties` + `DataCleanupConfiguration`.
- Scheduling: `@Scheduled(cron = "${artemis.scheduling.<name>-time:<default>}")`,
  with kill-switch booleans in the properties record defaulting to false.
  Template: `AutomaticDataCleanupScheduleService`.
- NOTE: A1's `WeaviateOutboxDispatcher` currently uses inline
  `@Value("${artemis.weaviate.outbox.batch-size:100}")` etc. This is off-convention
  and should be folded into a properties record in this branch.

---

## 1. Goal

The outbox guarantees that a change which REACHES it eventually lands in Weaviate.
A2 guarantees the index matches Postgres regardless of whether a change ever
reached the outbox.

A2 never writes to Weaviate itself. It only detects divergence and enqueues outbox
rows, so every write stays on A1's single-writer path.

## 2. Non-goals

- Any System B work (Iris/Pyris content pipelines).
- Rebuilding or replacing the outbox or the dispatcher.
- Completeness metrics and an admin view. PR #13431 already implements that via
  `IngestionCoverageWeaviateReadService` and `CoverageRecomputeService`. Ownership
  split: #13431 answers "is the index complete", A2 answers "make it complete".
  See settled decision 3.

---

## 3. Branch and stacking

Base: `feature/global-search/searchable-entity-outbox` (19 commits ahead of
`develop`, 0 behind, as of planning).

RISK, accepted by Nayer explicitly: PR #13446 is OPEN with `CHANGES_REQUESTED` and
21 reviews. This deviates from the ops-guide rule "never stack on another open PR".
Consequences:
- A2's diff carries all 19 outbox commits until A1 merges.
- Every review-driven change to A1 forces an A2 rebase.
- A reviewer-forced change to the ledger, the hash, or `source_seq` breaks A2's
  foundation.

Mitigation: keep A2 additive. Touch A1 files only through the narrow seams in
section 6, and keep the branch rebased.

---

## 4. Locked decisions

1. **Three independent passes**, separately scheduled, separately kill-switched:
   missing, drift, orphan. One failing never stops the others.
2. **Outbox depth is the master valve.** Before any pass enqueues, check pending
   outbox depth; above the threshold, skip the tick. This is a closed feedback
   loop, not a tuned rate: Weaviate slow or down means the queue stays deep and the
   reconciler stops on its own.
3. **NO priority column. The valve alone bounds live-write latency.** A priority
   column ordered `(priority, id)` was planned and is DROPPED after feasibility
   review (section 19.1). The claim query stays `ORDER BY id ASC`, and the valve
   default is kept LOW (500) so a live edit waits behind at most that many
   reconcile rows, which is seconds of drain and self-limits when Weaviate is slow.
   Reason: A1's bulk-delete fence silently assumes apply order equals outbox id
   order, and priority ordering breaks that assumption. The safe fix needs a
   monotonic apply-time sequence, which Artemis cannot express portably (no
   `createSequence` anywhere in its changelogs, and MySQL ships as a supported
   driver). If live latency ever proves to matter, priority plus the fence rework
   lands as its own focused PR where the fence change is the headline.
4. **Per-tick work budgets**, separate from the valve. The valve throttles OUTPUT
   (work handed to Weaviate). The budget throttles INPUT (questions asked of
   Postgres). In the healthy steady state nothing is enqueued, so the valve never
   fires and the budget is the only active protection.
5. **Slow cadence.** This is a convergence process, not a repair job. Budgets sized
   so a full sweep takes days. Target revisit period is the number to reason about.
6. **NO observer/dry-run mode.** Rejected by Nayer. Rollout safety comes instead
   from: flags default off, orphan GC armed last, the abort ratio, and the
   per-tick delete cap. These therefore carry more weight and must be strict.
7. **Nothing hardcoded.** All tunables go in a `@ConfigurationProperties` record
   following the `DataCleanupProperties` template. No constants at the top of files.
8. **`origin` column on the outbox** (`LIVE`, `RECONCILE_MISSING`,
   `RECONCILE_DRIFT`, `RECONCILE_ORPHAN`). Metrics become a `GROUP BY`, and a
   post-mortem can ask "why does this row exist" in SQL rather than by grepping
   logs. Completes the trace chain whose key is the outbox id. Kept even though
   priority was dropped: its value is diagnostic, not dispatch ordering.

---

## 5. Settled decisions

All four were open during planning and are now decided by Nayer. Nothing here is
still pending.

### 5.1 Cold start: re-enqueue the whole corpus, throttled

The ledger is empty on deploy day, but Weaviate is not: global search runs in
production and the collection already holds rows written by the old
fire-and-forget path. The missing sweep's rule is "no ledger row means never
indexed", so day one it reports the entire corpus as missing even though most of
it is present and fine.

Chosen: let it re-enqueue everything. Rejected alternatives were seeding the
ledger from a Weaviate scan, and admin-triggered only.

Why:
- Decision 5.4 forces the rewrite regardless. Existing rows carry no
  `content_hash`, so the verify pass treats them as unverified and rewrites them.
  Seeding would be work spent avoiding a rewrite that happens anyway.
- Seeding requires inventing a hash: either read and hash whatever stale content
  is there, or write a sentinel meaning "unknown" that makes the drift sweep
  re-check them all. Either way it records "synced" for rows nobody verified,
  which is the class of unchecked belief that produced this problem.
- Those rows were written by the old code. If any DTO gained or changed a field
  since, an unknown subset is stale. One rewrite is the only way to a known-good
  baseline.
- The cost is invisible: the valve only tops up the queue when the dispatcher has
  caught up, and the low valve setting bounds how long a live edit can wait
  behind it.
- Admin-triggered leaves old courses invisible until someone acts, which is the
  original problem (P9).

Day one is therefore not a special mode. It is the normal loop with a lot of work
to do, paced by machinery that already exists.

Resolves PER-92.

### 5.2 Posts and answer posts: excluded from `entity-types` by default

Posts and answer posts are plausibly 90% of the corpus. The drift sweep shares one
budget across all types, so including them does not merely add load, it dilutes the
sweep: courses and exercises inherit the posts revisit period, which at millions of
rows is roughly a month.

Excluding them changes nothing about normal indexing (the live outbox path is
untouched). What is given up is healing: a post write lost before it reaches the
outbox, or a drifted post row, is not repaired.

Chosen: ship excluded, with the code path fully supporting them. It is a yaml list,
so enabling them later is a config change, not a PR. Measure on the test server
first.

Consequence for pass 3: `entity-types` must gate all three passes identically. The
cursor scan sees post rows regardless of config and must SKIP out-of-scope types,
not check-and-delete them. Otherwise orphaned posts get deleted while no pass ever
re-adds a missing one, which is worse than not managing posts at all.

### 5.3 Metrics and admin view: none in A2

PR #13431 already computes expected-versus-actual per course per type and ships the
admin page. A second implementation on a second branch, in an area deliberately
serialized, would disagree at the edges.

Chosen: A2 ships no page and no new metrics system. Visibility comes from what the
design already produces:
1. `origin` on the outbox: `SELECT origin, count(*) FROM weaviate_outbox GROUP BY
   origin` gives queued reconcile work by pass.
2. `searchable_entity_reconcile_state`: per-pass cycle counters and positions.
3. The cycle-summary log line.

If the dashboard should later show reconciler activity, it reads A2's state table.

### 5.4 Stamp `content_hash` into the Weaviate row: yes

Taken. Full rationale in section 9. The version prefix (`v1:abc123`) is mandatory,
not optional.

Resolves PER-93.

---

## 6. Data model

### New Liquibase changelog

Alter `weaviate_outbox`:
- `origin` varchar(32) not null default 'LIVE'
- index supporting the pending-by-entity lookup

No `priority` column and no change to the claim query's `ORDER BY id ASC`. See
locked decision 3 and section 19.1.

Put these in a NEW changelog file, not A1's. A1's changelog is unshipped so editing
it is tempting, but if A1 merges to develop and a test server runs it before A2
lands, editing it breaks Liquibase checksums. Additive `addColumn` is safe either
way.

New table `searchable_entity_reconcile_state` (one row per pass):
- `id` bigint PK
- `pass` varchar not null unique (`MISSING`, `DRIFT`, `ORPHAN`)
- `position` varchar null (per-type id watermark for MISSING, ledger watermark
  for DRIFT, Weaviate cursor for ORPHAN)
- `cycle_started_at`, `last_run_at` timestamps
- counters for the current cycle (checked, repaired, removed)

Also add `CONTENT_HASH` to `SearchableEntitySchema` (no changelog needed;
`addMissingProperties` handles it at startup). See decision 5.4.

### A1 seams (kept deliberately small)

- `SearchableEntityWeaviateService`: extract the private `sha256Hex(serializeMap(..))`
  into a shared `SearchableEntityContentHash` utility; expose a generic
  `enqueueUpsert(type, entityId, origin)` / `enqueueDeleteEntity(type, entityId, origin)`.
- `WeaviateOutboxEntry` + `WeaviateOutboxRepository`: `origin`, pending-by-entity
  lookup, count. NO ordering change: the claim query is untouched, which keeps A1's
  bulk-delete fence valid.
- `WeaviateOutboxDispatcher`: fold its `@Value`s into a `WeaviateOutboxProperties`
  record. No logic change.

---

## 7. The three passes

### Pass 1: missing (DB -> ledger)

For each configured entity type, page over the indexable ids in Postgres in
ascending id order, then fetch the ledger's `entity_id` values for that same type
and id range in one query, and diff the two id sets in memory. An id absent from the
ledger was never confirmed written. Enqueue `UPSERT` with origin
`RECONCILE_MISSING`.

Two queries plus an in-memory diff, NOT a single anti-join. A JPQL anti-join would
have to join another module's entity against `SearchableEntitySyncState`, which the
module access rules forbid (section 19.2). Pages are bounded, so the extra query and
the in-memory set are trivial, and the same shape works uniformly for every type.

Id-only queries, no entity loading. This is what makes old and pre-feature courses
appear.

### Pass 2: drift (DB -> ledger content)

Take a bounded slice of ledger rows, oldest-verified first. For each, call
`SearchableEntityResolver.resolve(type, id)` and hash the result the same way the
dispatcher does.

- Hash matches: mark verified, move on.
- Hash differs: enqueue `UPSERT`, origin `RECONCILE_DRIFT`.
- Resolver empty (entity gone or no longer indexable, but the ledger still claims
  synced): enqueue `DELETE_ENTITY`, origin `RECONCILE_DRIFT`.

Ordering by oldest-verified means the corpus is swept continuously and every
entity is revisited on a fixed cycle, instead of a full scan per tick.

Note: this pass compares Postgres to the ledger. Both live in Postgres. It does
not read Weaviate. See section 9.

### Pass 3: orphan / verify (Weaviate -> DB)

Cursor scan `SearchableEntities` via `paginate()`, returning only `type`,
`entity_id`, `source_seq` and `content_hash`, a bounded number of pages per tick,
persisting the cursor so the next tick resumes.

Per scanned row, two checks:
- EXISTENCE. Batch-check ids against Postgres. A row whose entity does not exist is
  an orphan, from a lost delete or from the bulk-delete race A1 documented as
  residual. Enqueue a delete with origin `RECONCILE_ORPHAN`.
- CONTENT. Compare the row's `content_hash` against the ledger's. A mismatch means
  Weaviate diverged from what we believe we wrote (restore, silently lost write,
  manual edit, half-applied schema change). A missing hash means the row predates
  the stamp and is unverified. Both enqueue an `UPSERT` with origin
  `RECONCILE_ORPHAN`. This is the only check in the whole design that reads index
  content.

Guards, all mandatory because this is the only destructive pass:
- Skip rows whose `type` is not in `entity-types` (see decision 5.2). Skipping, not
  deleting: a type the other passes do not repair must not be deleted here.
- Skip rows with a pending outbox row.
- Fence on `source_seq` so a row written after the scan started is never touched.
- Cap deletes per tick.
- Abort the pass if the orphan ratio exceeds the configured threshold, which is
  the signature of a bug or a stale read rather than real orphans.
- Never treat a failed existence read as "not found". Any exception aborts the page.

---

## 8. Configuration

```yaml
artemis:
  weaviate:
    reconcile:
      missing-sweep-enabled: false
      drift-sweep-enabled: false
      orphan-sweep-enabled: false
      entity-types: [course, lecture, lecture_unit, exam, exercise, faq, channel]
      max-outbox-depth: 500           # the valve; kept LOW deliberately, see below
      missing-batch-size: 5000        # ids scanned per tick
      drift-batch-size: 200           # entities re-derived per tick
      orphan-page-size: 1000
      orphan-pages-per-tick: 5
      orphan-delete-cap-per-tick: 100
      orphan-abort-ratio: 0.25        # circuit breaker
  scheduling:
    weaviate-reconcile-drift-time:   "0 */5 * * * *"
    weaviate-reconcile-missing-time: "0 */10 * * * *"
    weaviate-reconcile-orphan-time:  "0 0 3 * * *"
```

`entity-types` deliberately omits `post` and `answer_post` (decision 5.2). The code
supports them; enabling them is a yaml change, not a PR. Revisit after measuring on
the test server.

`max-outbox-depth` is deliberately low. Since there is no priority column (locked
decision 3), it is the ONLY thing bounding how long a live metadata write can wait
behind reconcile work: a live edit queues behind at most this many rows. At 500 that
is seconds of drain, and it self-limits, because a slow Weaviate keeps the queue deep
and stops the reconciler from topping it up. Raising this trades live-write freshness
for backfill throughput; do not raise it without measuring drain rate first.

Bound by `WeaviateReconcileProperties`, a `@Validated @ConfigurationProperties(prefix
= "artemis.weaviate.reconcile", ignoreUnknownFields = false)` record with
`@DefaultValue` and `@Positive`, registered by `WeaviateReconcileConfiguration`.

---

## 9. Decision 5.4 in detail: stamp the content hash into the row (TAKEN)

### The change

1. Add `CONTENT_HASH` to `SearchableEntitySchema` (startup adds the column to
   existing collections automatically via `addMissingProperties`).
2. In `applyUpsert`, put it into `propertiesToWrite` next to `source_seq`.
3. Exclude it from the hash computation itself, same as `source_seq`, or it is
   self-referential.

### Why

Nothing in the reconciler otherwise reads the CONTENT of a Weaviate row. The
reconciler can prove a row EXISTS, and prove that what we believe we wrote is
current, but not that the row is CORRECT. If Weaviate holds something older than
the ledger claims, every check passes and the index stays wrong forever.

Causes: snapshot restore, a write that reported success but did not persist, a
manual edit, a partially applied schema change.

### What it buys

The orphan pass already walks every row. One more property in the projection turns
that walk into a full verification pass:

| Comparison | Catches | Covered without the stamp? |
|---|---|---|
| row exists vs DB entity exists | orphans | yes (orphan GC) |
| ledger hash vs re-derived hash | content drift | yes (drift sweep) |
| row hash vs ledger hash | Weaviate diverged from what we wrote | NO |

### Self-healing transition, and why it settles cold start

Rows written before the stamp have no `content_hash`. The scan treats a missing
hash as unverified and enqueues one re-upsert. That single pass rewrites every
pre-existing row with the current DTO shape, repairing the "old row is missing a
property added later" case, then never fires again.

This is why cold start (decision 5.1) needs no ledger seeding: the verification
pass IS the backfill.

### Cost, and the required mitigation

Any change to how the hash is computed invalidates every stored hash at once and
re-indexes the entire corpus. The valve absorbs it without hurting users, but it is
a footgun where a one-line change triggers a week of background writes.

REQUIRED and settled: version the hash, store `v1:abc123`. A deliberate
algorithm change then becomes distinguishable from corruption, and an older
version can be accepted as valid rather than treated as a mismatch.

Smaller costs: an operational field in a display/search collection (precedent:
`source_seq`), which must never leak into search results; larger objects and a
wider scan projection, both negligible.

---

## 10. Logging

THE RULE THAT MATTERS MOST: a tick that finds nothing logs nothing at INFO. These
passes run forever. If a quiet system is not quiet, the logs are worthless.

| Level | What |
|---|---|
| ERROR | pass aborted by the circuit breaker; sweep failing repeatedly |
| WARN | breaker tripped, delete cap hit, valve blocking for an extended period, a type skipped because its module is off |
| INFO | one line per tick ONLY when something was enqueued; effective config at startup; one summary line per completed sweep cycle |
| DEBUG | per-entity verdicts, sweep positions, cursor values |

The cycle-summary line is the health signal, e.g.
`drift sweep completed a cycle: 500123 verified, 47 drifted, 3 removed, 9d elapsed`.

Every line carries the pass name and a short run id so one tick's lines group
together. The end-to-end trace key is the outbox entry id, which A1 already logs at
DEBUG on both enqueue and apply; `origin` says why the row exists.

---

## 11. Robustness rules

- Each pass is independently failable. One throwing never stops the others.
- Positions persist. A restart resumes, never replays.
- No DB transaction is ever held across a Weaviate call (A1 already learned this).
- Never enqueue for an entity that already has a pending outbox row.
- THE DANGEROUS ONE: if a module is disabled, its `Optional<XRepositoryApi>` is
  empty, `SearchableEntityResolver` returns empty for every id of that type, and
  the drift sweep would enqueue a delete for every lecture while orphan GC deletes
  every lecture row. This needs an explicit "type unresolvable, skip entirely"
  guard, not a fallthrough. Must be covered by a test.
- Orphan GC never treats a failed read as "not found".
- Delete cap per tick plus the abort ratio.
- Everything idempotent: a duplicate enqueue is harmless (A1 collapses by entity).

---

## 12. Files

New, under `globalsearch`:

| File | Job |
|---|---|
| `config/WeaviateReconcileProperties` | validated record, `artemis.weaviate.reconcile` |
| `config/WeaviateReconcileConfiguration` | `@EnableConfigurationProperties` |
| `domain/SearchableEntityReconcileState` + repository | sweep positions and the Weaviate cursor |
| `service/reconcile/SearchableEntityReconcileScheduler` | the three `@Scheduled` entry points |
| `service/reconcile/ReconcileEnqueueService` | shared valve, dedupe, enqueue with origin |
| `service/reconcile/SearchableEntityMissingSweep` | pass 1 |
| `service/reconcile/SearchableEntityDriftSweep` | pass 2 |
| `service/reconcile/SearchableEntityOrphanSweep` | pass 3 |
| `service/SearchableEntityIndexScanService` | cursor scan of the collection |
| Liquibase changelog | reconcile state table, outbox `origin` (new file, not A1's) |

Plus per-type PAGED ID queries (not anti-joins, see section 19.2). Course, FAQ,
channel, post, answer post and exercise repositories are directly injectable
(those modules have no `api` package, which is why `SearchableEntityResolver`
already injects them directly). Lecture, lecture unit and exam need
`*RepositoryApi` additions.

CONFLICT WARNING: #13431 adds methods to those same three api files
(`findLectureIdCourseIdPairsForCourses`, `findIndexableUnitIdCourseIdPairsForCourses`,
`findExamIdCourseIdPairsForCourses`, plus an `IngestionCoverageExpectedIdsRepository`).
A conflict there is certain, not merely possible. Whichever merges second resolves it.

Touched in A1: see section 6 seams.

---

## 13. Commit sequence

1. Seams: hash utility, generic enqueue, `origin`, A1 config record.
2. Reconcile state table, properties, scheduler skeleton, enqueue service with the valve.
3. Missing sweep.
4. Drift sweep.
5. Orphan GC and the breaker.
6. Config docs and logging polish.

---

## 14. Tests

- Missing sweep: an entity with no ledger row is enqueued; one with a ledger row is not.
- Drift sweep: matching hash marks verified and enqueues nothing; differing hash
  enqueues an UPSERT; a resolver-empty entity enqueues a DELETE_ENTITY.
- Hash agreement: the reconciler's hash for an entity equals the dispatcher's for
  the same entity. Guards the "reports 100% drift forever" failure.
- Orphan sweep: a row with no DB entity is enqueued for deletion; a row with a
  pending outbox row is skipped; a row newer than the scan start is skipped.
- Verify: a row whose stored `content_hash` differs from the ledger is enqueued for
  re-upsert; a row with no stored hash is treated as unverified and re-upserted.
- Hash versioning: a stored hash with an older version prefix is not reported as
  corruption.
- Out-of-scope type: a `post` row encountered by the cursor scan is skipped, NOT
  deleted, while posts are absent from `entity-types`.
- Cold start overlap: with an empty ledger and unstamped rows, the missing sweep and
  the verify pass both target the same entity and only ONE outbox row results,
  because the second pass skips an entity that already has a pending row. Assert on
  pending rows, not on write count: collapse removes older rows when a newer one is
  applied, it does not skip pending work, so two rows would mean two writes. A race
  between the two passes can still produce a duplicate; that is harmless because
  re-derive is idempotent.
- Circuit breaker: an orphan ratio above the threshold aborts and deletes nothing.
- Delete cap: never more than the configured number per tick.
- Disabled module: with a type's repository API absent, the type is skipped and
  NOTHING is enqueued for it (neither delete nor upsert).
- Valve: with outbox depth above the threshold, no pass enqueues.
- Fence unaffected: A1's existing bulk-delete fence tests still pass unchanged,
  proving the claim ordering was not touched.
- Resumability: a pass restarted mid-cycle resumes from its persisted position.
- Schema/DTO agreement (optional guardrail, see section 15).

---

## 15. Known gap the reconciler structurally cannot close

A property declared in `SearchableEntitySchema.SCHEMA` but never written by any
`toPropertyMap()`. The map never contains it, the hash never changes, and nothing
is ever wrong from the reconciler's point of view. It converges Weaviate to the
DTO, and the DTO is what is incomplete.

That is a code problem, not data drift. Optional guardrail: a test asserting that
the schema's declared property names equal the union of keys produced by all nine
DTOs with every optional field populated, with a small allowlist for operational
properties (`source_seq` and `content_hash`). Could equally
ride in A1 or land on its own.

---

## 16. Acceptance criteria

- An entity in Postgres with no Weaviate row converges to indexed without manual action.
- An entity whose content changed without the index following converges to correct.
- A Weaviate row whose DB entity is gone is removed.
- A Weaviate row whose stored content differs from what the ledger
  claims is detected and rewritten.
- Nothing is enqueued when the corresponding pass flag is off.
- With all flags on and no divergence, INFO logs are silent and the Postgres query
  rate is flat and bounded by the configured budgets.
- Live metadata writes are not delayed by an in-progress backfill.
- Disabling a module does not cause its entity type to be deleted from the index.
- `./gradlew spotlessCheck checkstyleMain` and the new tests pass. Lint plus the
  touched tests locally; CI runs the rest.

---

## 17. Constraints

- Stacked on the outbox branch (deviation from the ops guide, accepted explicitly).
- Artemis Java conventions: records for DTOs, constructor injection, no wildcard
  imports, no new `@Transactional` scope, no `@Cache`, no direct Hazelcast/Redis.
- Configuration via `@ConfigurationProperties`, never inline `@Value` or file-top
  constants.
- No em dashes in PR text. Artemis PR checklist template.
- Never commit or push without explicit approval.

---

## 18. Linear

The existing project `Ingestion Backfill and Reconcile (Artemis)` (PER-116..128) is
STALE. It is written against the docs-page-16 `markDirty` + census design that never
shipped:

- Phase 1 (PER-116..124, nine "DB loader" tickets) is already delivered by
  `SearchableEntityResolver` in A1.
- PER-126 is blocked-by PER-99, which is Canceled.
- PER-127 (epoch/wipe detection) is partially superseded by decision 5.4: a missing
  or mismatched row hash detects a wipe or restore without a separate epoch object.
- `Ingestion Durable Sync` (PER-109..115) describes #13446 in a mechanism that does
  not match what was built.

Before executing A2, that project needs a re-plan against this spec.

Two of its blocking decision tickets are now answered by section 5 and should be
closed with the rationale recorded:
- PER-92 (old-course backfill scope) -> resolved by decision 5.1: proactive,
  throttled, not admin-triggered.
- PER-93 (stamp content hashes into Weaviate rows) -> resolved by decision 5.4: yes,
  with a version prefix.

---

## 19. Feasibility review findings

A full dry-run of this plan against the tree before starting. Each item was checked
in code, not reasoned about abstractly.

### 19.1 Priority ordering breaks A1's bulk-delete fence (BLOCKER, resolved by dropping priority)

A1's fence works because apply order equals outbox id order: rows are claimed
`ORDER BY id ASC`, an upsert stamps `source_seq = entry.getId()`
(`SearchableEntityWeaviateService:536`), and a bulk delete removes rows with
`source_seq < its own outbox id` (`writtenBefore`, line 647). Ordering by
`(priority, id)` destroys that invariant: a reconcile upsert with a LOWER id can be
applied AFTER a bulk delete with a higher id, stamping a value the delete's fence
then treats as older.

The safe fix is to stamp from a monotonic apply-time sequence instead of the outbox
id. That is NOT portably expressible here: there are ZERO `createSequence` uses in
Artemis's Liquibase changelogs, and both the MySQL and PostgreSQL drivers ship
(MySQL 8 has no sequences). The portable alternatives (an `apply_seq` column
allocated on first apply, or an auto-increment apply-log table) run 60 to 80 lines
across three files plus a changelog plus tests, and rewrite A1's concurrency
semantics on a PR already at 21 review rounds.

Honest note on severity: walking all five bulk-delete operations against their real
call sites, every one fires when the entities in scope are already deleted or
non-indexable in Postgres, so a competing upsert re-derives to nothing and converges
to a delete on its own. Four of the five are also type-filtered. No concrete
corruption scenario could be constructed from priority-without-the-fix today. But
the fence encodes an ordering assumption, and changing the ordering while leaving
the assumption in place is silently wrong the moment a sixth bulk-delete operation
with different semantics is added.

Resolution: drop priority (locked decision 3), keep the valve low, revisit as its
own PR if live latency ever proves to matter.

### 19.2 The missing sweep cannot be an anti-join (resolved by paged diff)

`AbstractModuleAccessArchitectureTest` IS instantiated, as `*ApiArchitectureTest`
for lecture, exam, iris, plagiarism, modeling, text, lti, atlas, tutorialgroup and
fileupload. It enforces that a class outside a module may only depend on that
module's `api`, `domain` and `dto` packages, and carries a second test asserting
repositories are never added to the ignore list ("cross-module access to a
repository must go through the module's api package, otherwise disabling the module
breaks application startup").

So a JPQL anti-join in `globalsearch` joining `Lecture` against
`SearchableEntitySyncState` is unavailable. Putting the anti-join in
`LectureRepository` instead would make the lecture module depend on the search
ledger; no rule currently blocks that, but it is a reverse dependency a reviewer
would reject.

Resolution: two queries plus an in-memory diff (pass 1).

### 19.3 Confirmed feasible

- `CollectionHandle.paginate()` (client6 6.2.1) supports `pageSize`, `filters`,
  `returnProperties` and `fromCursor` together, so a filtered resumable scan works
  and the 10,000-row `QUERY_MAXIMUM_RESULTS` offset cap does not apply.
- `WeaviateService.addMissingProperties` adds schema properties to existing
  collections at startup, so `CONTENT_HASH` needs no data migration. This is how
  `source_seq` landed.
- `Optional<XRepositoryApi>` gating is real for lecture and exam, confirming the
  disabled-module hazard in section 11 is a live risk and not theoretical.
- Course, communication and exercise have no `api` package, so their repositories
  are directly injectable; `SearchableEntityResolver` already does exactly this.
- `@Conditional(WeaviateEnabled)` plus `@Profile(PROFILE_SCHEDULING)` on a scheduled
  component has direct precedent in `WeaviateOutboxDispatcher`.
- Weaviate integration test infrastructure exists on the branch
  (`WeaviateOutboxIntegrationTest`).

### 19.4 Gotcha to encode carefully if posts are ever enabled

Channel indexability is `!archived && (courseWide || public)`
(`ChannelSearchableEntityDTO.isIndexable`). Post indexability is
`!archived && public` (`PostSearchableEntityDTO.isIndexable`). They DIFFER: a
course-wide but non-public channel is indexed while its posts are not. The expected
sets are therefore not interchangeable, and #13431's
`findIndexableChannelIdCourseIdPairsForCourses` covers only the channel predicate.
