Global search has two layers:

1. **Standard (metadata) search** - the existing production feature that matches courses, lectures, exercises, FAQs, and communication by keyword. Live today.
2. **Iris-powered search** - a semantic layer that searches inside lecture content, produces a grounded Iris answer with sources, and can hand off into an Iris chat. This is the redesign in progress.

Cutting across both layers is a **Weaviate ingestion durability** effort: making sure everything that should be in the search index actually is, and stays there, even when Artemis or Weaviate goes down. System A covers the standard-search metadata index (searchable entities); System B covers the Iris content collections (lecture pages, transcriptions, segments, FAQ content).

This page tracks what is done, what is in review, what is left, the blockers, and the timeline.

*Last updated: 10 Aug 2026.*

## Status key

* ✅ Done / merged
* 🟢 In review (code complete, awaiting review or merge)
* 🟡 In progress
* ⛔ Blocked
* ⚪ Planned / not started

## At a glance

| Workstream | Layer | Notes | Status |
|------------|-------|-------|--------|
| Durable metadata outbox (retry, no lost writes) | Standard | Artemis #13446, in review. First of two: outbox now, reconcile next | 🟢 In review |
| Metadata reconcile + backfill + orphan GC | Standard | System A / A2: heal old and never-indexed courses, self-heal drift, completeness metrics; also closes the outbox's residual atomicity gap | ⚪ Planned |
| Ingestion coverage dashboard | Standard | Artemis #13431 (draft): per-course and per-type database-vs-index coverage; trial branches show the fuller vision | 🟡 In progress |
| Standard search QA scenario suite | Standard | 10-15 non-trivial scenarios for CI | ⚪ Planned |
| One unified search box for lectures | Iris | Artemis #13314, awaiting Iris-team review + test-server sign-off | 🟢 In review |
| Access rights (course scope + staff visibility) | Iris | Artemis #13380 + Iris #712, must be tested together | 🟢 In review |
| Retrieval quality (reranker) | Iris | Iris #710 draft, blocked on the Logos reranker serving fix | ⛔ Blocked |
| Pyris content ingestion hardening (B1-B5) | Iris | Fail on partial writes, atomic re-ingest, audit + orphan GC, Artemis verify-against-reality, FAQ parity | ⚪ Planned |
| Multi-source Iris answers (searchable entities) | Iris | Depends on the reranker decision | ⚪ Not started |
| Chat handoff (Continue in Iris) | Iris | Rebuilt on current bases; an earlier attempt is reference only | ⚪ Not started |

---

## Standard (metadata) search - live in production

This is the baseline global search already shipped. Recent feedback ("production search did not find what was expected", not reproducible locally) pointed at a data problem rather than a code bug: some courses had only part of their data in Weaviate, and a failed write was silently lost. That is now being fixed structurally.

### 1. Durable ingestion: the outbox  🟢

Every metadata write (course, exercise, lecture, lecture unit, exam, FAQ, channel, post, answer post) used to be an async fire-and-forget call to Weaviate with log-and-forget error handling, so a Weaviate outage, a node dying, or an exception silently lost the write with no retry. This PR routes every write through a Postgres-backed outbox that a single scheduled dispatcher drains with retries and exponential backoff, re-deriving each entity's current state from the database at apply time. So no write is lost even if Artemis or Weaviate goes down; pending work survives in Postgres and is retried until it succeeds.

* Done: outbox table, sync-state ledger, single-writer dispatcher on the scheduling node, latest-wins collapse, and a source-sequence fence so a delayed bulk delete cannot erase a newer write. Validated end to end under Weaviate outage, node death, and out-of-order writes.
* To finish: address review, merge (Artemis #13446).
* Known limitation (deferred to the reconcile below): the source mutation and the outbox insert are separate commits, so a crash in that window can leave a changed entity un-enqueued. This is index-only drift; Postgres stays correct, and the reconcile heals it.

### 2. Reconcile, backfill, and orphan GC  ⚪

The second of the two standard-search PRs. A scheduled reconciler walks each entity type in the database in bounded, paged batches, computes the current content hash, and compares it to the sync-state ledger. Anything missing (never indexed, including old pre-feature courses) or drifted (hash mismatch) is enqueued to the outbox. It also does orphan GC: deleting index rows whose backing database entity no longer exists. Plus per-type and per-course completeness metrics, an admin view, and drift alerting, with the bulk backfill behind a feature toggle since it generates real Weaviate load.

* This makes old courses appear and any drift self-heal, forever, and it closes the residual atomicity gap left by the outbox (save-then-crash-before-enqueue).
* Status: planned, reuses the outbox and ledger from #13446.

### 3. Ingestion coverage dashboard  🟡

An admin-only observability dashboard that shows how completely each course is indexed. It compares what the database expects to be indexed against what the index actually holds, per course and per entity type, and surfaces gaps (missing) and stale entries.

* Status: Artemis #13431 (draft). Focused first cut; more ideas are queued but kept out to keep the diff reviewable.
* A fuller, richer version exists as a quick prototype (vibe-coded, for trial only, not going into the PRs): Artemis branch `feature/ingestion-observability-trial` and edutelligence branch `iris/ingestion-live-progress-trial`.

### 4. QA scenario suite for CI  ⚪

Goal: a set of 10 to 15 meaningful, non-trivial scenarios that run automatically in CI, so regressions in the standard search are caught before release.

Design principles:

* Not trivial single-hit cases. Each scenario is built on multiple courses, exercises, and lectures with similar or overlapping results, so the tests prove ranking and disambiguation, not just that a match exists.
* Cover the entity types the modal returns: courses, lectures, programming exercises, FAQs, and communication (posts and answer posts).
* Include cross-course name collisions, near-duplicate titles, partial-term queries, and permission-scoped visibility.

Status: to be drafted and reviewed before implementation.

---

## Iris search - the redesign

### One unified search box for lectures  🟢

Lecture content search used to live behind a separate button and a separate view. It now folds into the standard results list: selecting the **Lectures** filter runs Iris semantic content search, hits render inline with a location line (lecture name plus slide number or video timestamp), and a click lands on the exact slide or timestamp. When Iris is unavailable, it falls back to today's metadata search.

* Done: the full feature, plus removal of the old view and dead code.
* To finish: Iris-team review, test-server sign-off, merge (Artemis #13314). A small follow-up to suppress the error toast on fallback is queued.

### Access rights  🟢

Artemis resolves the requesting user's course access (course scope for everyone, plus a staff/admin bypass to see their own unreleased content) and forwards it to Iris, which applies it as a permission filter on both the results list and the Iris answer.

* Done: role resolution, the wire contract, and sending the context on both endpoints. The lecture metadata and visibility sync it builds on (Iris #708) is merged.
* To finish: address review on Artemis #13380, joint test-server verification of the pair (#13380 + Iris #712), the release-date-on-ingestion webhook, then merge.

### Retrieval quality (reranker)  ⛔

The retrieval pipeline was reworked into a reranked two-stage design to fix poorly ordered results and the answer bubble that appeared and then vanished. This is validated but blocked: see Blockers below. Iris #710 is a draft on purpose until the serving fix lands and the relevance threshold is recalibrated.

### Slide-range retrieval fix  ✅

A separate fix (Iris #623) for lecture units with many pages: a missing query limit made Weaviate silently cap results, so later pages were invisible to search. This PR has since been closed out; the truncation issue is no longer a standalone open item.

### Multi-source Iris answers (searchable entities redone)  ⚪

Extends the Iris answer to draw on Artemis entity data (exercises, FAQs, and more) alongside lecture content, and to ground "no result" answers in real existence counts. Not started. Depends on the reranker decision, because clean merging of the two data sources needs reliable relevance scores.

### Chat handoff  ⚪

After an answer, a **Continue** button opens the most relevant Iris chat (exercise, lecture, or course) pre-seeded with the question and answer. Not started. An earlier attempt will be redone on the current bases rather than carried forward, so only the routing logic serves as reference. Sequenced to land after the modal and searchable-entities work.

---

## Pyris content ingestion hardening (System B)  ⚪

The lecture-content pipeline (Iris `Lectures`, `LectureTranscriptions`, `LectureUnitSegments`, and FAQ content) already has a durable, scheduled, state-backed model on the Artemis side (the lecture-unit processing state plus scheduled retries and backfill). The hardening below makes its Weaviate writes trustworthy end to end. Planned, sequenced after System A, and split into small reviewable PRs.

* **B1 (Iris): fail on partial batch writes.** After each batch, inspect the failed-objects result and raise instead of reporting a silent success, across page, transcription, and FAQ ingestion. Small, no dependencies, ships first.
* **B2 (Iris): atomic, generation-based re-ingest.** Stamp every content object with a generation token, write the new generation first (never delete first), and commit by flipping a single `committed_generation` pointer on the unit row once the batch is verified complete. Retrieval filters by the committed generation. Replaces the destructive delete-first pattern so a re-ingest can never leave holes. Large and irreducible (write side and read filter must land together).
* **B3 (Iris): ingestion audit, counts, and orphan GC.** Add per-collection object counts to the terminal status payload, a `GET /lectures/{unitId}/ingestion-audit` endpoint, and a periodic sweep that deletes non-committed generations (the crash and Artemis-down backstop). Small to medium, depends on B2.
* **B4 (Artemis): verify against reality + extend the reconciler.** Gate the DONE transition on actual counts matching expected (not just `success`), add a reality-reconcile pass that compares DONE units against the audit endpoint and re-enqueues mismatches, widen backfill beyond active courses, make the concurrency limit configurable, and surface truthful status to instructors. Extends the existing System B scheduler, does not rebuild it. Medium, depends on B3.
* **B5 (Iris + Artemis, optional): FAQ content durable-state parity.** Give FAQ content ingestion the same Artemis-side durable processing state with retry and reconcile as lectures (today it is fire-and-forget). Small to medium, deferrable.

---

## Open pull requests

| PR | Delivers | State | To finish |
|----|----------|-------|-----------|
| Artemis #13446 | Durable outbox for metadata sync (System A / A1) | In review | Address review, merge |
| Artemis #13431 | Ingestion coverage dashboard | Draft | Finish first cut, open for review |
| Artemis #13314 | Unified lecture search box | In review | Iris-team review + test server, merge |
| Artemis #13380 | Resolve + forward access context | In review | Iris-team review, joint test server with Iris #712 |
| Iris #712 | Apply access context in search | In review | Iris-team review, joint test server with #13380, merge |
| Iris #710 | Reranked two-stage retrieval | Draft (blocked) | Serving fix, threshold recalibration, undraft |

---

## Blockers

### Reranker score compression (external, highest impact)

Global search reranks candidates with the Qwen3 reranker now served on Logos. The returned document scores are compressed: irrelevant results score almost as high as relevant ones, so junk is not pushed down. This weakens both retrieval-quality ranking and the planned multi-source answers (which rely on low scores to drop irrelevant sources).

* Reported to the responsible people. Waiting on a response (the owner was on vacation).
* Impact: gates retrieval quality (Iris #710) and the searchable-entities answers.
* Also open: a request to serve the reranker with the correct reranker chat template on Logos.

Until this is resolved, retrieval-quality tuning and multi-source answers stay parked, which is why the current focus is on the workstreams that do not depend on it (the outbox and reconcile, the ingestion dashboard, the unified search box, access rights, and standard-search QA).

---

## Sequencing

```
Standard (System A):  A1 outbox (#13446, in review) --> A2 reconcile + backfill + orphan GC (planned)
                      Ingestion dashboard (#13431) runs alongside
Iris content (System B):  B1 --> B2 --> B3 --> B4 (--> B5 optional)
Iris search UX:  unified box (#13314) + access rights (#13380 + Iris #712) [in review]
                 --> reranker (#710, blocked) --> multi-source answers --> chat handoff
```
