# Zoneless / Signals — remaining technical debt (follow-up backlog)

This file tracks the **deferred** follow-up work from the zoneless change-detection migration
(PR #12872). The core migration is complete: `provideZonelessChangeDetection()` is active, `zone.js`
is removed, `NgZone` is banned (ESLint `no-restricted-imports`), there are **0** legacy decorators
(`@Input`/`@Output`/`@ViewChild`/…), **0** legacy structural directives (`*ngIf`/`*ngFor`/`*ngSwitch`),
and only ~4 deliberately-justified manual change-detection sites remain.

The guidance for writing zoneless/signal code lives in
`documentation/docs/developer/guidelines/client-development.mdx`
("Zoneless change detection & signal-based state").

Estimates assume one experienced Angular dev and **include** spec updates + AOT build
(`pnpm run webapp:build`, the type arbiter) + targeted Playwright verification — because Vitest does
**not** prove zoneless rendering correctness (its `fixture.detectChanges()` calls hide the bug class).

---

## ✅ Done in PR #12872 (P1 — quick wins)

- Removed 4 genuinely-dead `ReactiveFormsModule` imports (`standardized-competency-filter`,
  `setup-passkey-modal`, `programming-exercise-version-control`, `range-slider`). Verified the other
  3 candidates (`competency-form`, `prerequisite-form`, `taxonomy-select`) actually use reactive
  forms and were left untouched.
- Removed 2 vestigial `ngOnChanges` hooks that could never fire (no Angular inputs; keyed on
  base-class signals): `quiz-re-evaluate.component.ts`, `quiz-exercise-update.component.ts`
  (+ updated their specs).
- Converted 4 `ngOnChanges` → constructor `effect()`:
  `common-course-competency-form.component.ts` and `online-unit-form.component.ts` (both only
  `patchValue` a reactive form from a signal input); `text-unit-form.component.ts` (its `content`
  field became a getter/setter-over-signal facade so the `[(markdown)]` two-way binding re-renders
  under zoneless); and `doughnut-chart.component.ts` (`receivedStats` became a `signal`, and the
  `chartEntries` read+write runs inside `untracked()` to avoid a self-triggering effect loop).

---

## P2 — Moderate (real work, bounded, behavior-sensitive)

> Do component-by-component with an AOT build + targeted Vitest + a targeted E2E check each.

### ✅ P2.1 — Convert the remaining `ngOnChanges` to reactive primitives — DONE

**Outcome:** Of the 15 components, **11 were migrated** to `computed()`/`effect()` and **4 kept `ngOnChanges`**
with a justified line-level `eslint-disable` (genuine `SimpleChanges.previousValue`/`isFirstChange()` /
before-`ngOnInit` timing): `feedback` (true→false print transition), `result`, `updating-result`, `image-cropper`.
The entire `remainingNgOnChangesMigrationBacklog` in `eslint.config.mjs` was cleared (incl. 8 already-stale P1
entries), so the `prefer-signal-reactivity-over-ngonchanges` warning now covers all non-spec client files.
Verified: `pnpm run lint` (0 errors / 0 new warnings), `pnpm run webapp:build` (AOT), 186 Vitest tests, and
targeted Playwright (DnD quiz editor, competency + prerequisite CRUD, lecture management — 18 passed / 0 failed).

The original breakdown and per-file classification (for history):

**Genuinely needs previous-value / `SimpleChanges` (hand-rolled previous-value tracking; some already
carry a justified `eslint-disable`):**
- `atlas/overview/competency-accordion/competency-accordion.component.ts` (branches on which input changed)
- `exercise/feedback/feedback.component.ts` (`changes.isPrinting` true→false transition)
- `exercise/rating/rating.component.ts` (reload only when `result.id` actually changes)
- `exercise/result/result.component.ts` (`isBuilding.previousValue` + which-input dispatch) — *eslint-disable present*
- `exercise/result/updating-result/updating-result.component.ts` (`participation.previousValue.id`) — *eslint-disable present*
- `plagiarism/manage/plagiarism-sidebar/plagiarism-sidebar.component.ts` (reset paging only on real change)
- `quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component.ts` (suppress emit on init via `previousValue`)
- `shared-ui/image-cropper/component/image-cropper.component.ts` (per-input dispatch + sync emit) — *eslint-disable present*

  → Pattern: snapshot inputs into a `previousInputs` field and diff inside an effect (see
  `code-editor-monaco.component.ts` for the established idiom), or keep `ngOnChanges` with a
  documented `eslint-disable` where `isFirstChange()`/`previousValue` is genuinely required.

**Runs before child init (form must exist before the child binds):**
- `atlas/manage/forms/competency/competency-form.component.ts` — already has a duplicate constructor
  `effect()`; likely just delete the hook after confirming ordering.
- `atlas/manage/forms/prerequisite/prerequisite-form.component.ts` — same.

**Side-effect orchestration (mechanical-but-fiddly; mostly plagiarism + pdf — convert to effects):**
- `lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component.ts`
- `plagiarism/manage/plagiarism-run-details/plagiarism-run-details.component.ts`
- `plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component.ts`
- `plagiarism/manage/plagiarism-split-view/split-pane-header/split-pane-header.component.ts`
- `plagiarism/manage/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component.ts`

**Estimate:** ~3–5 dev-days total. **Risk:** Low–Medium (each changes a real lifecycle path;
the `previousValue` ones are the most behavior-sensitive).

### ✅ P2.2 — `effect()` debt tail (data-fetch & derivation in effects) — DONE

**Outcome:** A careful review of the 8 flagged files reframed the scope: **5 were genuine misuse and were converted**,
**3 were justified and kept** (with documenting comments — forcing a conversion would have changed behaviour):

- ✅ `programming-exercise-update` — one-time localStorage `isSimpleMode` read moved out of a no-dependency effect into `ngOnInit`.
- ✅ `vcs-repository-access-log-view` — `effect(async … await …)` → `toSignal` of a `switchMap`'d stream (keeps the alert + previous entries on error).
- ✅ `instructor-submission-state` — stream→3-signals effect → `toSignal` of the exercise-id-keyed stream + 2 `computed`s.
- ✅ `exercise-title-channel-name` (PrimeNG + deprecated) → `toSignal` keyed on courseId+type (also fixes a subscription leak).
- ✅ `exam-students` — the `fetchExamData` effect → `toObservable(routeData)` bridge (`fetchExamData()` stays callable for imperative use).
- 🟡 `answer-post` — **kept**: the read+write is on a two-way `model()` (`posting`) whose write-back consumers depend on; the write is `untracked` + self-terminating. Documented inline.
- 🟡 `conversation-messages` — **kept**: 3 effects are genuine DOM/viewChild side effects; the `setPosts` one is imperative (HTTP + scroll + shared with the metis subscription). Documented inline.
- 🟡 `lecture-update` — **kept**: in-place entity mutation + subscription-management effects (legitimate). Documented inline.

Verified: Vitest for all touched specs, `pnpm run lint`, `pnpm run webapp:build` (AOT), targeted Playwright.

The original analysis (for history):

~383 real `effect()` calls across ~250 files; ~55–60% are legitimate (Monaco/PDF/scroll/`ResizeObserver`/
STOMP bridges with cleanup). The actionable subset:

**Data-fetching in an effect → `toSignal` / `httpResource` / `rxResource`:**
- `programming/.../vcs-repository-access-log-view.component.ts` (the lone `effect(async … await …)`)
- `exercise/.../exercise-title-channel-name…` (HTTP fetch of existing names)
- `exam/manage/students/exam-students.component.ts` (`fetchExamData`)
- `programming/.../instructor-submission-state.component.ts` (stream → sets 3 signals)

**Derivation in an effect → `computed()`:**
- `communication/answer-post/answer-post.component.ts` — reads `posting()` then `posting.set(...)`
  (**reads and writes the same signal** — highest priority; loop hazard)
- `communication/.../conversation-messages.component.ts` (`setPosts` rebuilds a displayed list)
- `lecture/manage/lecture-update/lecture-update.component.ts` (mutates a `model()` field in an effect)
- `programming/manage/update/programming-exercise-update.component.ts` (`initializeEditMode` reads
  localStorage + `.set()` once — one-time init misplaced into an effect)

**Leave as-is (document, do not refactor — high refactor risk, low payoff):** the heavy
`untracked`-guarded effects in `code-editor-monaco.component.ts` (input-diff cascade) and the iris
chatbot message-scroll effect. They work today; ~31% of effect-files already use `untracked()`.

**Estimate:** ~4 dev-days. **Risk:** Medium (changes loading/derivation semantics;
`answer-post`/`conversation-messages` are high-traffic communication).

### ✅ P2.3 — Harden remaining deep-entity `[(ngModel)]` for zoneless — DONE

**Outcome:** Full sweep of **~175 deep-entity `[(ngModel)]="entity.prop"` bindings across ~50 templates** (every
`x.y` and `signal().y` two-way binding in `src/main/webapp`). Each was classified against a zoneless-CD rubric
(target native vs CVA; source plain field vs `signal`/`input`/`model`/loop; reactive consumers; non-event
mutation sources) and cross-checked against each component's change-detection strategy. Result: **exactly one
genuine zoneless bug found and fixed; the other ~174 bindings are already zoneless-correct.** No speculative
facades were applied — that would be churn without payoff.

**The one fix:** `exercise/participation/participation.component.ts` `addGradedPresentation()` — on the
`maxNumberOfPresentationsExceeded` error branch the handler does only `dto.presentationScore = undefined` (no
alert, no signal write), so under zoneless nothing scheduled a change-detection pass and the rejected score
stayed in the input with the save button still showing. Fixed by re-emitting the `participations` signal
(`this.participations.update((p) => [...p])`); added a regression test asserting the re-emit.

**Why the other ~174 are safe (the four mechanisms — now documented in `client-development.mdx`):**
1. **Event-driven CD** — `[(ngModel)]` desugars to an `(ngModelChange)` template listener; template event
   listeners schedule a `tick()` under zoneless, so the input and same-component bindings re-render after edits.
2. **Default (CheckAlways) CD** — the large majority of these components are *not* `OnPush`; once any `tick()`
   runs (from any event/effect/signal/alert anywhere), they are always refreshed, so cross-field and
   sibling-section updates render. (The programming-exercise wizard sub-components are all Default CD, which is
   why the SCA-toggle / import-options / shortName "cross-component" concerns are non-issues.)
3. **Output emissions & alerts schedule CD** — an `output()` bound in a parent template, and `AlertService`
   showing an alert, both schedule a `tick()`; this rescues async error/load paths that re-emit or alert
   (e.g. `loadLongFeedback`'s `onFeedbackChange.emit`, exam-import's error alert).
4. **CVA `writeValue` self-schedules** — `jhi-date-time-picker` / `competency-selection` write a `signal` inside
   `writeValue`, so programmatic value changes propagate.
   → The lone failure mode is an **async callback that mutates state and schedules no tick at all** (no signal
   write, no template event, no alert, no output emit) — which is exactly the participation error branch above.

Verified: `pnpm run lint`, `pnpm run webapp:build` (AOT), participation Vitest (30 tests), targeted Playwright.

**Estimate (actual):** ~1 day. **Risk:** Low (one targeted fix; rest verified-safe, no behavior change).

---

## P3 — Strategic: migrate forms to Signal Forms (defer)

**Do not start until `@angular/forms/signals` leaves developer preview.** It is **experimental in
Angular 21** — the public API (`form`, `applyEach`, `validateTree`, `[formField]`) can break between
minors. The single pilot (`assessment/manage/grading/grading.component.ts`) already had to work around
real gaps: `[formField]` rejects optional and number fields (forcing a non-optional shadow model + a
getter/setter facade + hand-written handlers per optional/number field), plus an `NG0600` from a
side-effecting validator.

### P3.1 — Reactive forms → Signal Forms
48 genuine reactive forms. Buckets:
- **Large/complex (the real cost):** `course/manage/update/course-update.component.ts` (one `FormGroup`,
  ~39 `FormControl`s — the canonical big target), `admin/user-management/update/user-management-update`,
  `atlas/manage/generate-competencies/generate-competencies`, `exercise/submission-policy/submission-policy-update`,
  `admin/lti-configuration/edit/edit-lti-configuration`, `admin/system-notification-management/…-update`.
- **Medium (~20):** account register/settings/password, lecture-unit forms, tutorial-group config forms,
  atlas competency forms, category selectors, etc.
- **Small (~20):** communication posting/message inputs & dialogs, exercise-filter modal, course-request,
  confirm-entity-name, competency-recommendation-detail, feedback-detail-channel-modal.

### P3.2 — Deep-entity `[(ngModel)]` → Signal Forms
190 deep-entity `[(ngModel)]` bindings across 104 templates (the bulk of the cost). Heaviest:
`exam-update` (16), the quiz short-answer/drag-and-drop/update editors, `text`/`file-upload`-exercise-update,
programming task/language sub-components, `grading-instructions-details`, `external-submission-dialog`.

**Estimate:** ~6–10 dev-weeks if done wholesale. **Risk:** High (experimental API churn + validation/
behavior parity + E2E-only verification). **Recommendation:** keep the grading editor as the single
reference; revisit when the API stabilizes.

---

## Summary

| Priority | Scope | Effort | Risk |
| --- | --- | --- | --- |
| ✅ P1 | dead RFM imports, vestigial + mechanical `ngOnChanges` | done in #12872 | ~zero |
| ✅ P2.1 | remaining 15 `ngOnChanges` → signals (11 migrated, 4 justified-disable) | done | ~zero (verified: lint + AOT + Vitest + E2E) |
| ✅ P2.2 | `effect()` data-fetch/derivation tail (5 converted, 3 justified-kept) | done | Medium (verified) |
| ✅ P2.3 | harden deep-entity `[(ngModel)]` for zoneless (~175 swept; 1 fixed, ~174 verified-safe) | done | Low (verified: lint + AOT + Vitest + E2E) |
| P3 | forms → Signal Forms (reactive + template-driven) | ~6–10 weeks | High — **defer** until API stabilizes |
| ✅ P4 | make `ResultComponent` presentational (picking extracted, `model()`→`input()`, `ngOnChanges`→computed/effect) | done | Medium (verified: lint + AOT + Vitest + E2E) |

---

## P4 — Redesign `ResultComponent` (design improvement, not zoneless)

**Why.** `ResultComponent` accepts **three optional inputs** — `exercise`, `participation`, `result` — in several valid
combinations, and `ngOnInit` normalises them: derive `exercise` from the participation, *pick* the displayed result from
`participation.submissions` (first rated / first), derive `participation` from `result.submission.participation`, and clear
a non-displayable result. This ambiguous "pass any subset of 3" contract is the component's real complexity. It is also
why its `ngOnChanges` resists a clean signal migration: the hook reads `SimpleChanges.previousValue`, dispatches on *which*
input changed, and `ngOnInit`/`evaluate` write back to the same `result`/`participation` signals they read (an `effect`
that tracks-and-writes them loops). A faithful in-place conversion is therefore either a fragile shadow-field dance or a
large pure-`computed` rewrite — both of which **hide** the complexity rather than remove it. So `ResultComponent` keeps
its single documented `ngOnChanges` (justified inline `eslint-disable`) until this redesign lands.

**Proposed redesign — split by use-case so each piece has one clear contract:**
- A `pickDisplayedResult(participation, showUngradedResults)` helper/pipe (or a thin `ParticipationResultComponent`) owns
  the "which result of this participation do we show" logic. Call sites that today pass *only a participation* use it.
- A **presentational** `ResultComponent` takes an already-resolved `result` plus the minimal `exercise`/`participation`
  context it needs for rendering (rounding, student/practice checks, badge). No derivation, no picking, no `ngOnChanges`,
  no input/derived split — plain inputs, fully signal-based.
- Extract the shared score/status rendering into a dumb child both reuse.

**Migration.** Re-point the ~19 `jhi-result` call sites to the correct component/helper. Needs its own E2E across
exercise types (student/instructor; programming/modeling/text/quiz; exam vs course). Sizeable, but removes the essential
ambiguity instead of papering over it. **Do as its own focused PR** — not bundled with zoneless/`ngOnChanges` cleanup.
