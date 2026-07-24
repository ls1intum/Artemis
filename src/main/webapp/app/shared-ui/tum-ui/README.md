# tum-aet UI kit

Owned, dependency-light Angular UI components for Artemis (working name of the future
`@tumaet/ui-angular` library). This folder is an in-repo pilot: the components live here first so we
can iterate quickly against real admin screens, then extract them into a standalone package once the
API has settled.

## Why this exists

PrimeNG 22+ moved to a commercial license, so it is no longer a viable long-term dependency for an
open-source project. Rather than swap one third-party component library for another, we own a small
set of components built directly on **Angular CDK** (the unstyled, MIT-licensed behavior primitives)
and **Tailwind CSS v4** (our semantic design tokens). We control the behavior, the accessibility, and
the look, and we carry only a primitive (CDK) as a dependency. The same component contracts are meant
to be shared across TUM apps (Artemis, TumApply, and future Angular apps) so a TUM user learns each UI
concept once.

**Bootstrap and PrimeNG are deprecated.** The long-term goal is to migrate the client entirely onto
this owned kit and remove Bootstrap, PrimeNG, and ng-bootstrap. They stay installed only during the
migration: these components are additive and use distinct `tum-ui-*` selectors, so they coexist with
the PrimeNG and ng-bootstrap components still being migrated. Reach for a kit component first; fall
back to PrimeNG only for widgets the kit does not provide yet.

## Principles

- **Angular 21 signal APIs only**: `input()` / `input.required()`, `output()`, `model()`,
  `computed()`, `viewChild()`, `inject()`. No legacy decorators.
- **Standalone + `OnPush`**, zoneless-safe.
- **Token-only styling**: the same semantic Tailwind tokens Artemis' PrimeNG theme uses (`bg-primary`,
  `text-surface-*`, `text-muted-color`, `bg/text/border-state-*`), so the components render like the
  widgets they replace. Never raw Tailwind palette colors, `--p-*` primitives, or Bootstrap classes.
  Dark mode comes for free because the tokens resolve per theme.
- **Accessibility is part of the contract**: real semantics (`role`, `aria-*`), keyboard support, and
  focus management, verified in tests.
- **No PrimeNG / Bootstrap / ng-bootstrap** imports. `@angular/cdk` is the only UI _component-library_
  dependency (icon / date / utility libs like FontAwesome, `dayjs`, `lodash-es` are still used as needed).

## Components

| Component       | Selector                                   | Purpose                                                                                                                                    |
| --------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Button          | `tum-ui-button`                            | Native `<button>` with severity / size / outlined / text variants; forwards `ariaLabel` for icon-only use.                                 |
| Tag             | `tum-ui-tag`                               | Presentational status pill (tinted background, accessible label).                                                                          |
| Tooltip         | `[tumUiTooltip]`                           | Hover + focus tooltip on the shared overlay, with `aria-describedby` wiring.                                                               |
| Popover         | `tum-ui-popover` + `[tumUiPopoverTrigger]` | Content-projected `role="dialog"` panel; closes on backdrop click + Escape, traps focus.                                                   |
| Table           | `tum-ui-table<T>`                          | Generic CDK-table data grid: dynamic columns, single-column server-side sort, server-side paging, global search, row actions, striped / scrollable. |
| Paginator       | `tum-ui-paginator`                         | First / prev / numbered page buttons / next / last + page-size selector; emits `pageChange` / `pageSizeChange`.                            |
| Date picker     | `tum-ui-date-picker`                       | `dayjs`-backed date + time picker with a hand-built calendar; implements the Signal Forms `FormValueControl` contract.                     |
| Button (attr)   | `[tumUiButton]`                            | Attribute form of the button for a native `<a>` / `<button>` (keeps `routerLink`, `jhiDeleteButton`, etc.); mirrors `pButton`.             |
| Message         | `tum-ui-message`                           | Inline alert box; `severity` info / success / warn / error / secondary / contrast, `[text]` or projected content.                         |
| Input           | `[tumUiInput]` / `[tumUiTextarea]`         | Styles a native `<input>` / `<textarea>` (size, invalid state); composites `tum-ui-icon-field` and `tum-ui-input-group`(+`-addon`).        |
| Select          | `tum-ui-select`                            | Single-select dropdown on the shared overlay; `ControlValueAccessor` (ngModel / reactive forms).                                          |
| Checkbox        | `tum-ui-checkbox`                          | Binary checkbox over a hidden native input; CVA + `onChange`.                                                                             |
| Radio button    | `tum-ui-radio-button`                      | Radio over a hidden native input; CVA plus the one-way `[ngModel]` + `(onClick)` pattern.                                                  |
| Toggle switch   | `tum-ui-toggle-switch`                     | `role="switch"` on/off toggle; CVA + `changed`.                                                                                           |
| Select button   | `tum-ui-select-button`                     | Segmented single-select; CVA + optional `[itemTemplate]`.                                                                                 |
| Chip            | `tum-ui-chip`                              | Removable label pill; `(onRemove)`.                                                                                                       |
| Autocomplete    | `tum-ui-autocomplete`                      | Async multi-select combobox with token chips on the shared overlay; CVA + `completeMethod` / `onSelect` / `onUnselect`.                    |
| Progress bar    | `tum-ui-progress-bar`                      | Determinate bar with `[value]` and an optional label.                                                                                     |
| Progress spinner| `tum-ui-progress-spinner`                  | Indeterminate loading spinner (`role="status"`).                                                                                          |
| Card            | `tum-ui-card`                              | Surface card with `[tumUiCardHeader]` / body / `[tumUiCardFooter]` slots.                                                                  |
| Panel           | `tum-ui-panel`                             | Titled, optionally collapsible panel; `[header]` text or a `[tumUiPanelHeader]` slot for custom header markup.                            |
| Tabs            | `tum-ui-tabs` (+ `-tab-list` / `-tab` / `-tab-panels` / `-tab-panel`) | Tabbed navigation with a sliding active bar; `[value]` / `(valueChange)`.                    |
| Button group    | `tum-ui-button-group`                      | Visually joins adjacent buttons (collapses shared borders / inner radii).                                                                  |
| Dialog          | `tum-ui-dialog`                            | Declarative modal on the shared overlay; `[(visible)]`, `[header]`, `#header` / `#footer` slots, focus trap + Escape.                      |
| Basic table     | `[tumUiTable]` + `[tumUiSortableColumn]`   | Styles a native `<table>` (PrimeNG-matched) with 3-state sortable headers, for hand-templated grids; `tum-ui-table-virtual-scroll` for large lists. |
| Overlay service | `TumUiOverlayService`                      | Shared CDK overlay substrate (connected positioning, flip, reposition-on-scroll, backdrop) that tooltip / popover / date picker / select / autocomplete / dialog build on. |

Each component lives in its own folder with a colocated `*.spec.ts`. Variant class maps live in a small
`*.variants.ts` (a local, dependency-free take on class-variance-authority).

## Styling tokens

The components use the same semantic tokens Artemis' PrimeNG theme uses, so they render like the widgets
they replace:

- `bg-primary` / `text-primary` — the brand color (solid buttons fill it with `text-surface-0`).
- `bg/text/border-state-*` (`danger`, `success`, `warning`, `info`) — status colors; e.g. a tag uses a
  low-opacity `color-mix` tint of the state color (in its SCSS) with a state-colored label.
- `bg-surface-*` / `text-surface-*` / `text-muted-color` — the neutral surface ramp.

The `--danger` / `--success` / … custom properties differ per theme, so a single class is correct in
both light and dark without a `dark:` variant.

## Testing

Vitest, colocated with each component (the zoneless TestBed is initialized once globally, so specs call
`TestBed.configureTestingModule(...)` directly). Overlay geometry and real pointer interception are not
headless-verifiable, so specs assert the wiring and the semantics; placement, z-order, and pointer
capture are verified visually and in Playwright.

## Adding a component

1. Create a folder under `tum-ui/` with the component, an optional `*.variants.ts`, and a `*.spec.ts`.
2. Build on `@angular/cdk` primitives and `TumUiOverlayService` for anchored overlays.
3. Use only semantic tokens; add new tokens in the theme variable files + `tailwind.css` if needed.
4. Cover behavior, semantics, and (for colored surfaces) contrast.

See the full guide: **[UI Kit guidelines](../../../../../../documentation/docs/developer/guidelines/tum-ui-kit.mdx)**.
