# ng-bootstrap → PrimeNG Migration Plan

> Planning / reference doc for removing `@ng-bootstrap/ng-bootstrap` from the Artemis client and
> completing the migration to [PrimeNG](https://primeng.org/). Created alongside PR #13015
> (which removed `@angular/material`, `@angular/animations`, `@angular/localize`, `@sentry/core`
> and, as a side effect, the ng-bootstrap components that pull in `$localize`).
>
> **Status snapshot taken on branch `chore/remove-client-dependencies` (after PR #13015).** Re-run the
> counting commands below before starting work — numbers drift as `develop` advances.

## Why this migration

- `@ng-bootstrap/ng-bootstrap` is **deprecated for new code** in Artemis (see
  `documentation/docs/developer/guidelines/client-development.mdx`). All new UI must be PrimeNG.
- ng-bootstrap is **incompatible with Angular signal inputs**: assigning to a signal `input()` via
  `modalRef.componentInstance.X = Y` **silently fails**. Under zoneless this is a latent bug class,
  so migrating off ng-bootstrap also removes real correctness hazards — it is a *modernization*,
  not just a dependency swap.

## What PR #13015 already removed

These were migrated because `NgbPagination` / `NgbAlert` / `NgbProgressbar` (and the `NgbModule`
umbrella that re-exports them) call the global `$localize` at runtime, which blocked removing
`@angular/localize`:

| ng-bootstrap | → PrimeNG | reference impl |
|---|---|---|
| `NgbPagination` | `p-paginator` (0-indexed page; convert with `(event.page ?? 0) + 1`) | `atlas/manage/import-list/import-table`, `learning-paths-table`, `assessment/manage/rating/rating-list`, `programming/.../feedback-analysis`, `shared-ui/import/import.component` (base) |
| `NgbAlert` | `p-message` (`severity="info\|warn\|error\|success"`; `[closable]`/`(onClose)` for dismissible) | `assessment/manage/assessment-header`, `exam/.../exercise-group-update`, `programming/.../information\|problem\|grading` |
| `NgbProgressbar` | `p-progressbar` (`[value]` is **not** clamped — clamp to `[0,100]` yourself; use `#content`/`[showValue]` for labels) | `course/overview/course-dashboard`, `atlas/overview/competency-accordion`, `atlas/.../competency-contribution-card` |
| `NgbModule` (umbrella) | specific submodules (`NgbTooltipModule`, `NgbDropdownModule`, `NgbPopover`, …) | pdf-preview, theme-switch, faq, exercise-filter-modal |
| `NgbDateAdapter` / `NgbDateStruct` / `NgbDate` | (removed — dead after the `p-datepicker` migration #13009) | n/a |

Several **modals** were also migrated to PrimeNG `DynamicDialog` in this and prior PRs (see Modal below).

## Remaining surface (run before starting)

```bash
# distinct ng-bootstrap symbols + per-symbol counts in production code
grep -rl "@ng-bootstrap/ng-bootstrap" src/main/webapp --include='*.ts' | grep -v '\.spec\.ts'
# template directive usage per component
for d in ngbTooltip ngbPopover ngbDropdown ngbNav ngbCollapse ngbTypeahead ngb-accordion ngb-highlight; do
  echo "$d: $(grep -rl "$d" src/main/webapp --include='*.html' | wc -l) files"; done
```

## Per-component analysis

Difficulty/risk legend: 🟢 Low · 🟡 Medium · 🟠 Med–High · 🔴 High.
File counts are template-usage files as of PR #13015.

| Component | Files | PrimeNG replacement | Difficulty | Risk |
|---|---|---|---|---|
| Highlight (`ngb-highlight`) | 12 | *none* → small custom pipe/component | 🟢 | 🟢 |
| Accordion (`ngbAccordion`) | 2 | `p-accordion` (`p-accordion-panel`/`-header`/`-content`) | 🟢 | 🟢 |
| Collapse (`ngbCollapse`) | 10 | `@if`/`[hidden]` + CSS, or `p-panel [toggleable]` | 🟢–🟡 | 🟡 |
| Modal (`NgbModal`) | ~10 openers | `DialogService` + `DynamicDialog` | 🟡 *(modernizing)* | 🟡 |
| Tabs/Nav (`ngbNav`) | 5 | `p-tabs` (TabList/Tab/TabPanels/TabPanel) | 🟡 | 🟢–🟡 |
| Typeahead (`ngbTypeahead`) | 10 | `p-autoComplete` | 🟡 | 🟡 |
| Popover (`ngbPopover`) | 16 | `p-popover` (ex-OverlayPanel) | 🟡 | 🟡 |
| Tooltip (`ngbTooltip`) | 141 | `pTooltip` directive | 🟢 *per use* / 🟡 *(volume)* | 🟡 |
| Dropdown (`ngbDropdown`) | 32 (41 items) | `p-menu`/`p-tieredMenu` (menus) or `p-popover` (custom content) | 🟠 | 🟠 |

### Highlight — 🟢 Low / 🟢 Low — **best first win**
`<ngb-highlight [result]="text" [term]="search">` wraps matched substrings in `<span class="ngb-highlight">`.
No PrimeNG equivalent; replace with a ~20-line standalone pipe/component that does the same and is
easy to unit-test. Mechanical, self-contained.

### Accordion — 🟢 Low / 🟢 Low
`ngbAccordion` → `p-accordion`. Near-direct conceptual map; template restructuring of panels. Only 2 files.

### Collapse — 🟢–🟡 / 🟡
`[ngbCollapse]="isCollapsed"` is animated show/hide. PrimeNG has **no collapse directive** — use
`@if`/`[hidden]` (or `p-panel [toggleable]`). **Catch:** the smooth height animation must be redone in
CSS — you cannot rely on `@angular/animations` (removed in PR #13015). Functionally simple, small surface.

### Modal — 🟡 Medium *(modernizing)* / 🟡
**Do not over-rate this one.** The migration is repeatable and *removes* the signal-input hazard.

```ts
// ng-bootstrap (fragile: componentInstance.X = Y silently fails if X is a signal input())
const ref = this.modalService.open(MyDialog);
ref.componentInstance.course = this.course;
ref.result.then((r) => …);

// PrimeNG DynamicDialog (typed data channel, signal-friendly)
const ref = this.dialogService.open(MyDialog, {
    data: { course: this.course }, header, modal: true, dismissableMask: true, closeOnEscape: true,
});
ref.onClose.subscribe((r) => …);
// inside MyDialog:
private config = inject(DynamicDialogConfig);
readonly course = this.config.data.course;
```

Each migrated modal changes both the opener and the dialog component, but the pattern is proven.
Reference impls: `iris/.../exercise-chatbot-button`, `communication/.../posting-content-part`
(enlarge-slide-image), `communication/.../conversation-members`, `admin/user-management/.../user-management-update`
(organization selector), `exam/.../exam-import`, `lecture/.../lecture-import`, `shared-ui/import/import.component`.
Watch: focus/escape behavior, result handling (promise → `onClose` observable), and dialog content that
used `@angular/animations` for enter/leave (re-express in CSS).

### Tabs/Nav — 🟡 / 🟢–🟡
`ngbNav` + `ngbNavOutlet` → `p-tabs`. Mostly template restructuring (5 files). The tricky part is that
ng-bootstrap **decouples the nav from its outlet** (`ngbNavOutlet` can live elsewhere in the DOM) and
content is lazy (`ngbNavContent`). If a usage relies on a detached outlet or lazy panels, plan for it.

### Typeahead — 🟡 / 🟡
`[ngbTypeahead]="obsFn"` → `p-autoComplete` (`[suggestions]` + `(completeMethod)` + `(onSelect)`, item
template via `#item`). **Pattern already proven** in PR #13015 (`category-selector-primeng`,
`user-management-update` groups). Work: convert the Observable-returning search fn to the imperative
"set a suggestions signal in `completeMethod`" model; preserve debounce / min-length / formatter parity.

### Popover — 🟡 / 🟡
`[ngbPopover]` (directive; inline/template content; hover **or** click) → `p-popover` is a separate
`<p-popover>` element toggled imperatively (`(click)="op.toggle($event)"`), **click-only by default**.
Move content into the `<p-popover>` and wire a toggle. Hover-triggered popovers don't map 1:1.

### Tooltip — 🟢 *per use* / 🟡 *(volume = 141 files)*
`[ngbTooltip]="text"` → `pTooltip="text"`; `placement` → `tooltipPosition`; `container="body"` →
`[appendTo]="'body'"`; `disableTooltip` → `[tooltipDisabled]`. Each swap is trivial; the challenge is
**scale + two edge cases**:
1. **Template tooltips** (`[ngbTooltip]="tplRef"`) — `pTooltip` is text-only; those few need `p-popover`.
2. **Global default** — PR #13015 restored a global `NgbTooltipConfig` in `app.main.ts`
   (`container='body'` + disable-on-Handset). PrimeNG has no global equivalent, so this must be applied
   per element (`[appendTo]="'body'"`) or via a thin wrapper directive. **Remove that `app.main.ts` block
   only once the last `ngbTooltip` is gone.**

Best executed as a scripted codemod + spot-checks, not by hand.

### Dropdown — 🟠 Med–High / 🟠 — **hardest remaining**
ng-bootstrap dropdowns are **template-driven** (`<button ngbDropdownItem (click)=…>`), PrimeNG
`p-menu`/`p-tieredMenu` are **model-driven** (`MenuItem[]` in TS with `command` callbacks). Converting
the 41 items means moving items, icons, i18n labels, `disabled`/conditional logic and especially
**`routerLink`s** (awkward in the `MenuItem` model) out of templates into typed models — or, for
dropdowns holding arbitrary content (not a menu), using `p-popover`. High chance of behavioral drift.

## Suggested order (low-risk → high-risk)

**Highlight → Accordion → Collapse → Modal → Tabs → Typeahead → Popover → Tooltip (codemod) → Dropdown.**

- Tooltip is "easy but huge" — do it as one scripted sweep.
- Dropdown and Modal deserve dedicated PRs (Dropdown for the model shift; Modal because it touches both
  opener and component per dialog, though it's the lowest-risk of the "hard" ones).

## Cross-cutting gotchas

- **Zoneless / signals:** template-bound mutable state must be a `signal`; update collections immutably;
  prefer `computed` over recompute. PrimeNG components play well with signals; ng-bootstrap's
  `componentInstance` mutation does not. (See `client-development.mdx`.)
- **`@angular/animations` is gone** (PR #13015): any ng-bootstrap component relying on enter/leave or
  height animations (collapse, modal/dialog transitions) needs the animation re-expressed in CSS.
- **PrimeNG internal styling:** overriding a PrimeNG component's internals from a component stylesheet
  still requires `::ng-deep` (deprecated but unavoidable for this); scope it under the host/`styleClass`
  and add a justifying comment. Prefer global theme overrides or the pass-through (`pt`) API where practical.
- **Verification:** `pnpm exec vitest run` uses esbuild and **does not type-check** — always run
  `pnpm run compile:tests` (tsc) and `pnpm run webapp:prod` (AOT, type-checks templates) before pushing.
  The Playwright E2E suite is the real arbiter for "does it re-render in the running app".
