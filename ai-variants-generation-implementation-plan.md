# AI Exercise Variants Generation — Implementation Plan

**Goal:** Make the existing "Create Variant with AI" button (currently a UI-only wizard) generate real, high-quality exercise variants — starting with programming and quiz exercises, with an architecture that extends to modeling, text, and file-upload exercises with minimal effort.

**Team & timeline:** Two master students, one week, AI coding tools. Programming + quiz must be production-quality by the end of the week; other types are architecture-proven stubs / future work.

---

## 1. Goal, Scope, and Quality Bar

### What a "variant" is

A variant is a copy of a source exercise, transformed along instructor-selected dimensions (the wizard already collects these):

- **Difficulty change** — easier or harder while keeping the topic (e.g. remove/add tasks, simplify/extend the required algorithm, adjust test strictness).
- **Domain change** — re-theme the exercise story/domain (e.g. "bank accounts" → "space station inventory") while preserving grading semantics.
- **Custom instructions** — free-text transformation requests.

The variant is placed standalone, in an existing `ExerciseVariantGroup`, or in a newly created group (all three placement paths already exist server-side).

### Quality bar (what "done" means)

| Exercise type | A generated variant MUST |
|---|---|
| Programming | Compile and pass **100% of tests in the solution repository**; **fail tests in the template repository**; have a consistent problem statement (tasks reference real test names); have unique short name / project key |
| Quiz | Pass `QuizExercise.isValid()` (every question valid: correct mappings, ≥1 correct MC option, valid SA spots/solutions); pass `validateQuizExerciseFiles` for DnD |
| All | Be consistent with the source exercise except for the requested changes; be immediately editable by the instructor through the normal editors |

A variant that fails the final gates is **kept as a clearly flagged draft** (never silently published, never silently deleted) so the instructor can repair it in the editor — this respects the "system must recover from errors" requirement without throwing away expensive LLM work.

### Explicit non-goals for week 1

- Modeling/text/file-upload variants beyond the thin proof-of-extensibility adapter (Section 8).
- Regenerating drag-and-drop background/item **images** (source images are carried over unchanged).
- Exam-mode variants.

---

## 2. Architecture

### 2.1 Overview

The feature is a new Hyperion sub-module (`hyperion/service/variants/` + `hyperion/web/HyperionExerciseVariantResource.java`), because Hyperion already owns the Spring AI `ChatClient`, prompt templating, token accounting, the async-job + WebSocket pattern, and — critically — a working **commit → push → trigger CI → wait for result** loop.

```
Wizard (existing UI) ──POST /api/hyperion/exercises/{id}/generate-variant──▶ jobId
        ▲                                                                    │
        │  WebSocket progress events (phase transitions, files, attempts)    ▼
        └──────────────  ExerciseVariantOrchestrator (async job)  ───────────┘
                                       │
   ┌───────────┬─────────────┬─────────┴────────┬──────────────┬─────────────┐
   ANALYZING   PLANNING      PROVISIONING       TRANSFORMING   VERIFYING     FINALIZING
   render      LLM produces  clone exercise     agent loop     deterministic assign group,
   source      ChangePlan    via existing       edits the      + semantic    publish DONE
   context     (structured)  ImportService      copy via tools gates         event
                                                      ▲              │
                                                      └──REPAIRING◀──┘  (bounded loop)
```

Design principle throughout: **the orchestrator, planner, agent loop, and job/progress infrastructure are written once and are exercise-type-agnostic; everything type-specific lives behind small capability interfaces.**

### 2.2 Explicit phase state machine (not an implicit call chain)

A variant job advances through typed phases:

```java
enum VariantJobPhase {
    ANALYZING, PLANNING, PROVISIONING, TRANSFORMING, VERIFYING, REPAIRING, FINALIZING,
    COMPLETED, DRAFT_WITH_WARNINGS, FAILED
}
```

The job record (jobId, exerciseId, phase, attempt counter, `ChangePlan`, accumulated verifier findings, token usage) lives in a **Hazelcast map**, exactly like `HyperionCodeGenerationJobService.JobInfo` — distributed-safe, deduplicates concurrent jobs per exercise, survives the request thread.

**Why a state machine instead of one long method:** multi-minute jobs need attributable failures ("failed in VERIFYING, attempt 2/3, template build passed when it should fail"), per-phase recovery policies (Section 6), a 1:1 mapping to the wizard's existing `GENERATION_STEPS` progress UI, and per-phase telemetry for the thesis evaluation (Section 7).

### 2.3 Ports & adapters for exercise-type variability

**Rejected alternative:** one `VariantGenerationStrategy` per exercise type that owns its whole pipeline. This duplicates orchestration/repair/progress logic N times and makes every cross-cutting quality improvement N-times work.

Instead, the orchestrator depends on five small capability interfaces; each exercise type contributes one adapter per capability:

```java
interface VariantContextRenderer  { String renderContext(Exercise source); }
interface ExerciseProvisioner     { Exercise provision(Exercise source, VariantRequest req); }   // clone + uniqueness
interface VariantToolsetFactory   { List<ToolCallback> createTools(Exercise variant, VariantJob job); }
interface VariantVerifier         { VerificationReport verify(Exercise variant, ChangePlan plan); }
interface VariantFinalizer        { void finalize(Exercise variant, VariantRequest req); }        // group assignment, publish
```

A `VariantTypeRegistry` resolves the adapter bundle from Spring-injected lists keyed by `ExerciseType` — the standard Spring idiom (inject `List<T>`, pick by `supports(...)`), applied at the *capability* level rather than the pipeline level. Adding a new exercise type = implementing thin adapters; the orchestrator, planner, agent loop, job infra, REST API, and client are untouched.

Most adapters are wrappers around existing, battle-tested services (Section 3/4) — very little new logic.

### 2.4 Structured planner before any edits (plan-then-execute)

The PLANNING phase is a dedicated LLM call with structured output (`BeanOutputConverter`, the established Hyperion pattern):

**Input:** rendered source-exercise context + wizard intent (target difficulty / domain text / custom instructions).

**Output — `ChangePlan` record:**
- the rewritten problem statement,
- an ordered list of concrete intended changes ("rename `BankAccount` → `CargoBay` across all repos", "remove task 3 and tests `testSortDescending*`"),
- **invariants to preserve** ("grading semantics unchanged: same number of tasks and weights", "test names referenced in problem statement tasks must exist in test repo").

The plan is stored on the job — inspectable in the UI, loggable, and directly evaluable for the thesis ("did the executed diff match the plan?"). It becomes the agent's contract in the TRANSFORMING phase.

**Why not encode intents as code-level step combinations** (e.g. a `DifficultyTransformation` + `DomainTransformation` class hierarchy): the intent space is open-ended (free-text custom instructions), so this variability belongs in the planner prompt, not the type system. Code-level intent branching adds classes without adding output quality.

### 2.5 Hybrid agentic core: one generic tool-calling loop for Transform + Repair

The deterministic scaffold (cloning, orchestration, final gates, group assignment) is classic code. The TRANSFORMING/REPAIRING phases run a **single reusable Spring AI tool-calling agent loop**, parameterized only by:

- the type's toolset (from `VariantToolsetFactory`),
- the `ChangePlan` (system prompt contract),
- an iteration budget (≈3–5 verify cycles) and a token budget (tracked via `LLMTokenUsageService`).

Toolsets per type:

| Programming | Quiz |
|---|---|
| `listFiles(repoType)` | `getQuestions()` |
| `readFile(repoType, path)` | `updateQuestion(index, questionJson)` |
| `applyEdit(repoType, path, search, replace)` / `writeFile` | `validateQuiz()` → wraps `isValid()` + per-question error details |
| `runBuild(repoType)` → commit, push, trigger CI (existing plumbing) | `finish(summary)` |
| `getBuildAndTestResults(repoType)` → compiler output + failed test names/messages | |
| `finish(summary)` | |

**Why an agent with tools instead of fixed prompt sequences:** correct variants require *closed-loop* behavior — reading the real compiler error or the real failing-test message and deciding what to fix. Fixed pipelines approximate this poorly (one hardcoded "repair prompt" per failure class). And because the loop is generic, all quality investment (loop robustness, budget handling, transcript logging) lands in one place and benefits every exercise type.

**Why not fully agentic end-to-end** (agent also clones, sets metadata, assigns groups): unbounded cost/latency, much harder to test deterministically, and the deterministic parts already exist as reliable services — an LLM re-doing them adds risk, not quality.

**Why not an external coding agent** (e.g. invoking a CLI agent on the checked-out repo): highest raw code quality, but a new runtime/ops dependency, credential surface, and deployment story — not appropriate for Artemis core and not needed, since Spring AI tool calling is already in-house (`SpringAIConfiguration.chatClient`).

Spring AI implementation note: register tools as `ToolCallback`s on the `ChatClient` call; Spring AI executes the tool-call loop internally per call — wrap it in an outer loop bounded by the verify-iteration budget, injecting the latest `VerificationReport` into each round's user message.

### 2.6 Composite verification chain — deterministic ground truth first

`VariantVerifier`s run in a fixed order, cheapest and most objective first:

1. **Programming:** solution build passes 100% / template build fails — reusing `HyperionCodeGenerationExecutionService`'s `waitForBuildResult` + `hasReachedTargetResult` semantics (these already encode exactly this rule per `RepositoryType`).
2. **Quiz:** `QuizExercise.isValid()` + `validateQuizExerciseFiles` (structural correctness incl. DnD file references).
3. **All types (semantic gate):** consistency check between problem statement and artifacts, reusing `HyperionConsistencyCheckService` (structural + semantic checks already implemented for programming exercises).

Findings are returned as structured data and fed back into the agent loop as the repair signal. If the budget is exhausted with failures remaining → `DRAFT_WITH_WARNINGS` with the findings attached. **Never silent success.**

---

## 3. Programming Exercise Pipeline (Student A focus)

All building blocks exist; the work is composition + prompts.

| Phase | What happens | Existing code reused |
|---|---|---|
| ANALYZING | Render problem statement + template/solution/test repo contents as LLM context | `HyperionProgrammingExerciseContextRendererService.renderContext(exercise)` |
| PLANNING | `ChangePlan` LLM call (Section 2.4); rewritten problem statement produced here | `HyperionPromptTemplateService`, `BeanOutputConverter`; new prompt `prompts/hyperion/variants/plan_programming.st` |
| PROVISIONING | Clone entity + repos + build plans; set title/short name from wizard; on collision retry with suffix (`-V2`, `-V3`) | `ProgrammingExerciseImportService.importProgrammingExercise(...)`, `ProgrammingExerciseValidationService.validateNewProgrammingExerciseSettings` / `checkIfProjectExists` |
| TRANSFORMING | Agent loop edits the **copied** repos (diff-style edits of existing files — *transform, don't regenerate*, this is the main consistency lever) and writes the new problem statement | `GitService.getOrCheckoutRepository` / `commitAndPush`, `RepositoryService.createFile/getFiles/commitChanges`; new prompt `variants/transform_programming_system.st` |
| VERIFYING / REPAIRING | `runBuild` tool: commit+push, trigger build, wait for result; solution must pass, template must fail; failures (compiler output, failed tests) go back to the agent; ≤ N attempts | `ContinuousIntegrationTriggerService.triggerBuild`, `HyperionCodeGenerationExecutionService.waitUntilRemoteHasCommit` / `waitForBuildResult` / `hasReachedTargetResult` / `BuildResultOutcome` (extract into a shared helper both codegen and variants use) |
| FINALIZING | Persist problem statement, assign variant group per placement choice, publish DONE with new exercise id | `ExerciseVariantGroupResource` assignment logic (reuse the service-level path behind `PUT .../variant-group`), existing group-creation endpoint |

**Transformation order that minimizes iterations:** solution repo first (until green), then test repo only if the plan changes tests (then re-verify solution), then template (must fail), problem statement last (agent has final test names in context). The planner is instructed to prefer plans that keep the test surface stable when the intent allows it (domain re-theme ⇒ rename-only test changes).

**Refactor note:** `waitForBuildResult` & friends currently live privately in `HyperionCodeGenerationExecutionService`. Day-1 task: extract them into e.g. `HyperionBuildVerificationService` used by both features — do not copy-paste the polling loop.

---

## 4. Quiz Exercise Pipeline (Student B focus)

Simpler — no repos, no CI; validation is synchronous and cheap, so agent iterations are fast.

| Phase | What happens | Existing code reused |
|---|---|---|
| ANALYZING | Serialize quiz (questions, options, mappings, scoring types) as LLM context — new small renderer adapter | quiz domain model (`MultipleChoiceQuestion`, `DragAndDropQuestion`, `ShortAnswerQuestion`) |
| PLANNING | `ChangePlan`: per-question intended change + invariants (keep scoring type, keep points, preserve # of correct options unless difficulty change says otherwise) | new prompt `variants/plan_quiz.st`, modeled on `generate_quiz_questions_*` |
| PROVISIONING | Deep-copy the quiz incl. DnD images and batches | `QuizExerciseImportService.importQuizExercise(...)` (handles MC/DnD/SA copies, `copyDragItemFile`, mappings) |
| TRANSFORMING | Agent rewrites questions via `updateQuestion` tool (JSON per question, schema-validated), calls `validateQuiz` as it goes; DnD: only text/mapping edits, images carried over | `HyperionQuizQuestionGenerationService` prompt patterns + refinement prompts; `GeneratedQuizQuestionDTO` as the tool's JSON schema |
| VERIFYING / REPAIRING | `isValid()` + `validateQuizExerciseFiles` + an LLM self-critique pass (reuse the existing quiz refinement prompts as a critique step: "is the distractor set plausible? is exactly the requested change applied?") | `QuizExercise.isValid()`, `QuizExerciseService.validateQuizExerciseFiles`, refinement prompts |
| FINALIZING | Persist, assign group, DONE event | same as programming |

---

## 5. API, Job Infrastructure, and Client Changes

### 5.1 REST API (one endpoint for all types)

```
POST /api/hyperion/exercises/{exerciseId}/generate-variant   → 200 { jobId }
GET  /api/hyperion/exercises/{exerciseId}/generate-variant/active → job status (reconnect/dedup)
```

`@EnforceAtLeastEditorInCourse`, matching the wizard button's visibility condition. Request DTO mirrors the wizard state exactly:

```java
record VariantGenerationRequestDTO(
    boolean changeDifficulty, DifficultyLevel targetDifficulty,
    boolean changeDomain, String domainText,
    boolean changeCustom, String additionalInstructions,
    String title, PlacementDTO placement /* EXISTING_GROUP(groupId) | NEW_GROUP(fields) | STANDALONE */) {}
```

The exercise type is read from the source exercise server-side; the registry resolves the adapters. Unsupported types → 400 with a translatable error key (client hides/disables the button per type anyway).

### 5.2 Job + progress infrastructure

Clone the proven trio, generalized for variants:

- `ExerciseVariantJobService` — Hazelcast map, `startJob` / `getActiveJob` dedup (mirrors `HyperionCodeGenerationJobService`).
- `ExerciseVariantTaskService.runJobAsync(...)` — `@Async`, drives the orchestrator (mirrors `HyperionCodeGenerationTaskService`).
- Events over `HyperionWebsocketService` on `/user/topic/hyperion/variant-generation/jobs/{jobId}`:

```java
record VariantGenerationEventDTO(Type type /* PHASE_CHANGED, PROGRESS, ATTEMPT, DONE, FAILED */,
    VariantJobPhase phase, Integer attempt, Integer maxAttempts, String detail,
    Long variantExerciseId, List<String> warnings) {}
```

Phase transitions map 1:1 onto the wizard's existing `GENERATION_STEPS` labels ("Building solution repository", "Verifying 100% test score", …) — the fake progress UI becomes the real one with minimal template change.

### 5.3 Client changes (small — the wizard already exists)

1. New `ExerciseVariantGenerationService` (Angular) calling the endpoint (via the regenerated OpenAPI client, like `hyperionCodeGenerationApi`).
2. In `exercise-variant-ai-modal-wizard.component.ts`: replace the `setInterval` mock in `startGeneration()` with the POST + a WebSocket subscription (reuse the `hyperion-websocket.service.ts` `subscribeToJob` pattern); drive step index from `PHASE_CHANGED` events; on `DONE`, fetch the created exercise and show the real result step (incl. `DRAFT_WITH_WARNINGS` state with the warnings listed).
3. Delete the mock generation from `exercise-variant-ai-modal.utils.ts`.
4. **Bind `(variantAdded)` in `exercise-actions.component.html`** (currently unbound!) and bubble it to `course-management-exercises.component.ts` to insert the new row / refresh groups via the existing `ExerciseVariantGroupService` load path. Placement in an existing/new group happens server-side in FINALIZING; the client only refreshes.
5. Resume support: on wizard open, call the `active` endpoint to re-attach to a running job.

---

## 6. Error Handling & Recovery Matrix

| Failure | Phase | Policy |
|---|---|---|
| Short name / project key collision | PROVISIONING | Deterministic retry with suffix (`-V2`…), re-running `checkIfProjectExists`; never ask the LLM |
| Malformed planner/tool output | PLANNING / TRANSFORMING | `BeanOutputConverter` / JSON-schema validation error is returned **to the model** as the tool result; 2 re-prompts, then FAILED |
| Solution build red / template build green | VERIFYING | Structured failure report (compiler output, failing test list) into the agent's next round; ≤ 3–5 attempts |
| Quiz `isValid()` false | VERIFYING | Per-question validation errors into agent's next round (cheap, sync) |
| Budget exhausted, gates still red | REPAIRING | `DRAFT_WITH_WARNINGS`: variant kept, flagged in result step + exercise detail; instructor repairs in editor |
| Hard failure before exercise exists (LLM unavailable, provisioning exception) | ≤ PROVISIONING | FAILED event; delete any half-created exercise via the existing exercise deletion service (repos + build plans cleaned up) |
| Server restart mid-job | any | Hazelcast job record marks it stale; `active` endpoint reports FAILED-stale; provisioned exercise (if any) surfaces as draft. (Full resume = future work) |
| CI timeout | VERIFYING | Counts as a failed attempt with a distinct `detail`; reuse `BuildResultState.TIMED_OUT` semantics |

---

## 7. Quality Measures & Evaluation (advisor expectation)

Prompt/design levers for output quality:
- **Plan-then-execute** (Section 2.4): forces the model to commit to changes + invariants before touching files.
- **Transform-not-regenerate:** the agent edits the cloned artifacts; unchanged code stays byte-identical to the source — the single biggest consistency lever.
- **Closed-loop repair on real signals:** compiler output and failing test names, not LLM self-assessment.
- **Deterministic gates the model cannot talk its way past** (builds, `isValid()`, consistency check).
- Per-job telemetry: tokens (`LLMTokenUsageService`), attempts per phase, wall time.

Evaluation protocol (days 6–7, feeds the thesis):
1. Corpus: ~5 real programming exercises (varying language/size) × 3 intents (easier, harder, domain change) + ~5 quizzes × 3 intents.
2. Automatic metrics: first-attempt build pass rate, final pass rate within budget, mean repair iterations, consistency-check issue count, token cost & latency per variant.
3. Manual rubric (both students independently score 1–5): correctness of the requested change, preservation of everything else, problem statement quality, "would I release this to students as-is?".
4. Failure taxonomy for the writeup (planner error vs. transform error vs. verification gap).

---

## 8. Extensibility Proof: Modeling / Text / File-Upload (stretch or future work)

Each needs only thin adapters — the orchestrator/planner/agent/API/client are shared:

- **Provisioner:** delegate to `ModelingExerciseImportService` / `TextExerciseImportService` / `FileUploadExerciseImportService` (all extend `ExerciseImportService`; pure DB clones, no repos/CI — the simplest possible adapters).
- **Toolset:** `getProblemStatement`/`setProblemStatement`, `getExampleSolution`/`setExampleSolution`; modeling adds `getSolutionModel`/`setSolutionModel` (Apollon JSON, schema-validated in the tool).
- **Verifier:** schema/structural validation + the semantic consistency gate.
- **Finalizer:** shared implementation already works (group assignment is type-agnostic).

Estimated effort once programming + quiz exist: ~1 day per type including prompts. Recommended as future work in the thesis unless the week runs ahead of schedule.

---

## 9. Work Breakdown — Two Students, One Week

**Days 1–2 (pair, shared skeleton):**
- D1 AM: extract `waitForBuildResult`/`waitUntilRemoteHasCommit`/`hasReachedTargetResult` into a shared `HyperionBuildVerificationService` (pure refactor + existing tests still green).
- D1 PM: job infra (`ExerciseVariantJobService`, `TaskService`, event DTO, WebSocket topic), REST endpoint + request DTO, `VariantTypeRegistry` + capability interfaces, phase machine in `ExerciseVariantOrchestrator`.
- D2: generic agent-loop runner (ChatClient + ToolCallbacks + budgets + transcript logging); planner phase with `ChangePlan`; client wiring (service, WebSocket subscription, bind `variantAdded`) against a stub toolset that echoes the source exercise — **end of day 2 milestone: wizard drives a real job end-to-end producing an unmodified clone.**

**Days 3–5 (parallel):**
- **Student A — programming:** provisioner adapter over `ProgrammingExerciseImportService` (+ short-name retry); repo toolset over `GitService`/`RepositoryService`; `runBuild`/`getBuildAndTestResults` tools over the extracted build-verification service; prompts (`plan_programming.st`, `transform_programming_system.st`); transformation-order policy; budget tuning. Milestone D4: "make it easier" variant of a real Java exercise goes green unattended; D5: domain change + custom instructions green.
- **Student B — quiz:** context renderer + provisioner over `QuizExerciseImportService`; question toolset with JSON-schema validation; prompts (plan, transform, self-critique reusing refinement prompts); consistency-gate integration for both types; wizard polish (attempt display, warnings state, resume via `active` endpoint). Milestone D4: MC + SA variants valid unattended; D5: DnD (text-level) variants valid.

**Days 6–7 (pair):** integration + failure-path testing (collision, budget exhaustion, LLM outage); prompt tuning driven by the evaluation corpus; run the evaluation protocol (Section 7); tests + lint; screenshots + PR.

AI coding tools are assumed for: adapter/test scaffolding, prompt iteration, and the evaluation harness script — the plan's estimates already price this in.

---

## 10. Testing Plan

- **Server integration tests** (JUnit, Testcontainers/PostgreSQL, mocked `ChatClient` — the established Hyperion test pattern): orchestrator phase transitions incl. failure paths; provisioning collision retry; scripted agent-loop runs (mock ChatClient returns canned tool calls) asserting repo edits land and builds are triggered; quiz adapter round-trip asserting `isValid()`.
- **Unit tests:** `VariantTypeRegistry` resolution, `ChangePlan` (de)serialization, tool input validation, budget enforcement.
- **Client tests (Vitest):** wizard drives steps from WebSocket events; `DONE`/`DRAFT_WITH_WARNINGS`/`FAILED` rendering; `variantAdded` refresh path.
- **E2E (Playwright, `./run-e2e-tests-local-fast.sh --filter "Variant"`):** full wizard flow against a mocked-LLM profile (deterministic canned plan + edits), asserting the variant appears in the course exercise list / group.
- **Multi-node sanity:** one manual `run-e2e-tests-local-multinode-fast.sh` run before the PR — the job map is Hazelcast-backed and the WebSocket event must reach the user regardless of which node runs the job.

---

## 11. Future Work

- Full modeling/text/file-upload transformation depth (Section 8).
- DnD image regeneration / image-aware re-theming.
- Difficulty calibration using the existing checklist/Bloom analysis (`HyperionChecklistService`) as a planner input and post-hoc difficulty verifier.
- Job resume after server restart; batch generation ("create 4 variants for group X").
- Exam-mode variant groups.
