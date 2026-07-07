# AI Exercise Variants Generation — Implementation Report

**Date:** 2026-07-07
**Branch:** `feature/exercise-variants-ai-generation` (16 commits, ~8,900 insertions across 77 files)
**Base / merge target:** `feature/exercise-variants-support` (stacked PR — **not** `develop`)
**Plan:** `ai-variants-generation-implementation-plan.md`

---

## 1. Current State

The feature is **functionally complete for programming and quiz exercises** and has been verified end-to-end, both manually against a real LLM + real local CI and in automated integration tests. What remains before the PR is test coverage at the E2E level, OpenAPI client regeneration, a handful of small cleanups, and the thesis evaluation protocol.

### 1.1 Backend (`hyperion/service/variants/`, ~3,500 lines)

| Plan section | Component | Status |
|---|---|---|
| 2.2 | `VariantJobPhase` state machine, `VariantJob` Hazelcast record (24 h TTL), `ExerciseVariantJobService` (single-writer mutation API, publishes matching WS event per state change) | ✅ Done |
| 2.2 | `ExerciseVariantGenerationPipeline` — full phase machine: ANALYZING → PLANNING (structured `ChangePlan`, 2 re-prompts on malformed output) → PROVISIONING → bounded TRANSFORMING/VERIFYING/REPAIRING loop → FINALIZING; cancel checks at phase boundaries; clone cleanup on failure/cancel; `DRAFT_WITH_WARNINGS` on budget exhaustion | ✅ Done |
| 2.3 | Five capability interfaces + `VariantTypeAdapters` bundle + `VariantTypeRegistry` (fail-fast duplicate check) | ✅ Done |
| 2.4 | Planner with `BeanOutputConverter`, plan validation (`validatePlan` rejects blank title/statement, empty `intendedChanges` — retry path exercised live), per-phase `StepOutput` records (truncated before Hazelcast storage) | ✅ Done |
| 2.5 | `VariantAgentLoopRunner` — one Spring AI tool-calling round per verify cycle, `VerificationReport` injected as repair signal, per-round tool-call budget (25 quiz / 60 programming), `finish` is returnDirect, token budget enforced across rounds | ✅ Done |
| 2.6 | Composite verification: programming = solution green + template red (fresh, `notBefore`-bounded build results) + consistency-check semantic gate; quiz = per-question/quiz `isValid()` + `validateQuizExerciseFiles` + LLM self-critique soft gate | ✅ Done |
| 3 | `ProgrammingVariantAdapters` + `ProgrammingVariantTools` (listFiles, readFile, applyEdit with unique-match + write-whitelist, writeFile, updateProblemStatement with task re-extraction, runBuild = commit+push+trigger+wait, getBuildAndTestResults, finish); short-name derivation with `-V2…` collision retry over the VCS/CI pre-check; `VariantBuildVerificationService` (standalone, mirrors codegen target-result semantics — codegen infra deliberately untouched per review feedback) | ✅ Done |
| 4 | `QuizVariantAdapters` + `QuizVariantTools` (getQuestions/updateQuestion in the editor's polymorphic JSON, type-preserving, DnD image paths locked, statistics carried over on save); group placement forces the clone to `QuizMode.INDIVIDUAL` and drops copied batches | ✅ Done |
| 5.1 | `HyperionExerciseVariantResource`: generate, job list, job detail, cancel (409 from FINALIZING on / terminal). **Deviation from plan:** no per-exercise dedup lock and no `active` endpoint — parallel jobs on the same exercise are an explicit product requirement | ✅ Done |
| 5.2 | Dedicated `hyperionVariantTaskExecutor` (shared pool would starve at 2 threads), initiator impersonation in `runJobAsync`, WS events on `/user/topic/hyperion/variant-generation/jobs/{jobId}` | ✅ Done |
| 5.5 (server) | `SAME_EXAM_GROUP` placement — exam variant lands in the source's exam exercise group | ✅ Done |
| 6 | Recovery matrix: collision retry, malformed-output re-prompts, structured build-failure feedback, budget exhaustion → draft, hard-failure clone cleanup, FINALIZING failure downgrades to `DRAFT_WITH_WARNINGS` (never deletes verified work), CI timeout as distinct finding detail | ✅ Done (except server-restart staleness, see §2.1) |
| 7 (partial) | Token accounting: planning / transform / critique / failure-summary calls all report via `LLMTokenUsageService` (pipeline ids `exercise-variant-plan/-transform/-critique/-failure-summary`), running total on the job + `totalTokensUsed` in DTO/modal | ✅ Done |
| — | AI failure post-mortem: on FAILED, one best-effort LLM call (`failure_summary.st`) generates an instructor "what happened & how to continue" summary, stored before the FAILED transition; static fallback | ✅ Done (beyond plan) |

All 6 prompt templates exist (`plan_/transform_` × programming/quiz, `critique_quiz_system.st`, `failure_summary.st`), tuned after live failures (problem statement is an exercise field, not a repo file).

### 1.2 Client

- **Wizard** (`exercise-variant-ai-modal-wizard.component.ts`): drives the real backend job — POST via generated OpenAPI client, per-job WS subscription, step timeline derived from `VariantJobPhase` (REPAIRING = repeat visit with attempt counter), expandable step-output panels (STEP_OUTPUT events trigger a job-detail fetch for full logs), source → variant flow card + adaptation chips, token-total chip, DONE/`DRAFT_WITH_WARNINGS`/FAILED/CANCELLED result states, cancel behind confirmation (close ≠ cancel), monitor mode for tray reopening, `baseZIndex` 2000 fix. Exam exercises skip the placement step (`SAME_EXAM_GROUP` forced). All three placements wired (STANDALONE / EXISTING_GROUP incl. sourceGroup loading / NEW_GROUP with full group form).
- **Navbar tray** (`variant-generation-tray.component.ts`): right icon menu, wand icon with status overlay (spinner/check/warning, no count badge), step-dot timeline per entry, card click opens the monitor modal, cancel action, job list synced on auth-state changes.
- `variantAdded` bubbles wizard → exercise-actions → exercise-table → course-management-exercises (course view reload).
- **OpenAPI:** `openapi.yaml` deliberately contains only HEAD + variant endpoints; generated client files partially hand-maintained (`variantJob.ts` `totalTokensUsed`) — see §2.2.

### 1.3 Tests & verification

- **Server:** `ExerciseVariantGenerationIntegrationTest` — 10 green tests (happy path over REST driving the *real* quiz tools from a scripted ChatModel, malformed-planner FAILED path incl. post-mortem, budget exhaustion → draft, cooperative cancellation with clone cleanup, REST validation 400s, per-user scoping, parallel jobs, NEW_GROUP + exam placement). `VariantTypeRegistryTest` + `ChangePlan` round-trip unit tests.
- **Client (Vitest):** generation-service and tray specs (parallel jobs, auth-driven sync, entry policies); full client suite green.
- **Manual (LM Studio gpt-oss-20b + real local CI):** quiz variants COMPLETED and DRAFT_WITH_WARNINGS with parallel jobs; programming pipeline end-to-end incl. collision retry and budget exhaustion; second run fully green COMPLETED on attempt 1/3 (~5 min, ~19.5 k tokens) with the planner-retry path exercised live.
- **Multi-node sanity run: PASSED (2026-07-07,** `run-e2e-tests-local-multinode-fast.sh`**)** — cross-node job list/detail/step outputs, WS delivery via ActiveMQ relay in both directions, cross-node cooperative cancel, variant readable cross-node. (The latest commit message still lists this as a next step; it was completed afterwards.)
- **E2E (Playwright):** `ExerciseVariantGeneration.spec.ts` is still a **skipped stub** — the main open test item.

### 1.4 Known environment observations (not code defects)

- LM Studio fails parallel chat calls ("Channel Error") → the consistency-check semantic gate silently no-ops locally (best-effort by design; active with real providers). It also serializes concurrent calls, so parallel jobs starve each other locally.
- gpt-oss-20b leaks raw harmony markup (`<|channel|>final …`) into the agent finish summary shown in the TRANSFORMING step output — cosmetic; a `<|…|>` strip is a candidate cleanup.
- Local dev runs need `ARTEMIS_VERSIONCONTROL_URL=http://localhost:8080` (localvc default 8000 has no listener); multi-node runs need the Spring AI env vars and the local `SPRING_JPA_DATABASE=POSTGRESQL` workaround for the develop-side buildagent dialect regression (kept uncommitted on purpose).

---

## 2. Open Items

### 2.1 Genuinely open TODOs in code

| Location | Item |
|---|---|
| `src/test/playwright/e2e/exercise/ExerciseVariantGeneration.spec.ts` | Implement the full wizard flow against a mocked-LLM profile (skipped stub with implementation guide in the header) |
| `VerificationReport.java` | Turn the `gate` string into a small enum; add `pass()` factory + `toAgentFeedback()` convenience (minor cleanup) |
| `VariantJob.java:84` | Staleness mechanism for server restart mid-job. Note: the TODO text references the `active` endpoint, which was **removed** with the dedup lock — reword to target the tray list (a restarted node leaves jobs forever "running" in the tray until TTL). Plan Section 6 allows reporting FAILED-stale; full resume is future work |

### 2.2 Open non-TODO items

- **OpenAPI client regeneration.** A full regen is currently blocked by develop-side generator breakage (tutorial-group dual legacy/plural mappings → duplicate method names; `TutorialGroupSummary` date-type regression). Until resolved (or resolved on the base branch), the hand-maintained files (`variantJob.ts` etc.) must be kept generator-faithful; regenerate before the PR if possible, otherwise document the deviation in the PR.
- **Exam-mode entry point (client).** Plan Section 5.5(a) — the "Create Variant with AI" button is **not yet shown on exam exercise rows** (`src/main/webapp/app/exam/manage/` has no variant action). Wizard + server already fully support exam variants (`SAME_EXAM_GROUP` forced, placement step skipped), so this is small wiring, but without it exam variants are unreachable from the UI.
- **Stale TODO comments to remove** (implemented, comment not yet deleted — verify each and call the removal out in the commit, per working agreement): `VariantFinalizer` (both blocks — shared finalizer + draft finalization exist in `VariantPlacementService`/pipeline line 157), `VariantContextRenderer` (both adapters implemented), `ExerciseProvisioner` (all three — incl. hard-failure cleanup in the pipeline), `ChangePlan` (round-trip unit-tested; `validatePlan` enforces validity), `StepOutput` (pipeline `truncate()` bounds every stored detail).
- **Evaluation protocol** (plan Section 7) — not started; feeds the thesis.
- Optional polish: strip `<|…|>` harmony tokens from agent finish summaries (model-specific cosmetic).

---

## 3. Roadmap (in execution order)

### Before the PR

1. **Remove the stale TODO comments** listed in §2.2 after verifying each against the implementation; reword the `VariantJob` staleness TODO to drop the removed-`active`-endpoint reference. (Small, unblocks an honest TODO signal for everything after.)
2. **Exam-mode client entry point:** show the "Create Variant with AI" action on exercise rows in the exam exercise-group management view, opening the existing wizard (placement auto-skipped). Add a Vitest spec for the exam path (placement step hidden, `SAME_EXAM_GROUP` sent).
3. **Implement `ExerciseVariantGeneration.spec.ts` (Playwright):** full wizard flow against a deterministic mocked-LLM profile (canned ChangePlan + edits), asserting the variant appears in the course exercise list/group; run via `./run-e2e-tests-local-fast.sh --filter "ExerciseVariantGeneration"`.
4. **OpenAPI client regeneration:** attempt the full regen; if the develop-side generator breakage still blocks it, keep the scoped `openapi.yaml` + hand-maintained models and note it in the PR description.
5. **Small cleanups:** `VerificationReport` gate enum + `pass()`/`toAgentFeedback()`; optional harmony-markup strip in finish summaries.
6. **Job staleness on restart:** either implement the minimal FAILED-stale marking (heartbeat timestamp on the job record, tray/list marks jobs stale past a threshold) or explicitly declare it future work in the PR — decide, don't leave it implicit.
7. **Pre-PR gate:** `./gradlew spotlessCheck checkstyleMain -x webapp`, `pnpm run lint`, full server + client test suites, screenshots of wizard/tray/modal states, PR against `feature/exercise-variants-support` using the PR template. (Multi-node sanity is already ✅.)

### After the PR (thesis work, this branch or follow-up)

8. **Evaluation protocol (plan Section 7):** corpus of ~5 programming exercises × 3 intents + ~5 quizzes × 3 intents against a capable provider (not LM Studio — parallel-call limitation disables the semantic gate); collect first-attempt pass rate, final pass rate, repair iterations, consistency issues, token cost & latency; two-rater manual rubric; failure taxonomy.

### Future work (plan Sections 8 & 11 — explicitly out of scope for this PR)

- Modeling / text / file-upload adapters (thin adapter bundles; Apollon-JSON-validated modeling toolset; ~1 day per type).
- DnD background/item **image** regeneration and image-aware re-theming.
- Difficulty calibration via `HyperionChecklistService` as planner input + post-hoc verifier.
- Full job resume after server restart (beyond FAILED-stale marking); batch generation ("create 4 variants for group X").
- DB-backed job audit table persisting finished-job history beyond the Hazelcast 24 h TTL.
