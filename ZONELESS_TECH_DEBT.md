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

### P2.1 — Convert the remaining `ngOnChanges` to reactive primitives

15 components still implement `ngOnChanges`. Breakdown and per-file classification:

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

### P2.2 — `effect()` debt tail (data-fetch & derivation in effects)

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

### P2.3 — Harden remaining deep-entity `[(ngModel)]` for zoneless

Not a Signal Forms migration — just ensure any `[(ngModel)]="entity.prop"` that misbehaves under
zoneless uses the established getter/setter-over-signal facade (most were already swept; residual risk
is Low given E2E is green). Audit + fix as found.

**Estimate:** ~3–5 dev-days. **Risk:** Low–Medium.

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
| P2.1 | remaining 15 `ngOnChanges` → signals | ~3–5 days | Low–Medium |
| P2.2 | `effect()` data-fetch/derivation tail (~8 files) | ~4 days | Medium |
| P2.3 | harden deep-entity `[(ngModel)]` for zoneless | ~3–5 days | Low–Medium |
| P3 | forms → Signal Forms (reactive + template-driven) | ~6–10 weeks | High — **defer** until API stabilizes |
