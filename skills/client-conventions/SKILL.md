---
name: client-conventions
description: Apply Artemis conventions when changing Angular application or TUM UI code, migrating components, or fixing client lint violations.
---

# Artemis client conventions

Use `reference/migration-recipes.md` for migration examples. Check changes with `pnpm run lint`
and `pnpm run prettier:check`. Prefer standalone components.

## Signals are mandatory for new code

Use `input()` / `input.required()`, `output()`, `viewChild()` / `viewChild.required()`,
`viewChildren()`, `signal()`, `computed()`, `effect()`, and `inject()` for dependency injection.

The legacy decorators `@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, and
`@ContentChildren` must not appear in new code. Enforced by `localRules/enforce-signal-apis`
(`rules/enforce-signal-apis.mjs`) in modules that have been migrated.

In a module that is not yet fully migrated, prefer signals for new components but stay consistent
within an existing component. Do not half-migrate a component.

## `ngOnChanges` is banned

Use `computed()` or `effect()`. Enforced at error level by
`localRules/prefer-signal-reactivity-over-ngonchanges` (`rules/prefer-signal-reactivity-over-ngonchanges.mjs`)
across `src/main/webapp/app`, `packages/tum-ui/src/lib`, and `src/test/javascript`, including specs
and undecorated base classes.

This is a consistency ban, not a correctness fix. Angular does call inherited `ngOnChanges` hooks
and does fire them for signal inputs, so existing uses are not dead code.

A genuinely unavoidable case, meaning you need `SimpleChanges.previousValue` or `isFirstChange()`,
or ordering before child initialisation, needs a detailed comment and a justified line-level
`eslint-disable-next-line`. `ngOnInit` and `ngOnDestroy` are unaffected.

## Template control flow

Use `@if`, `@for`, `@switch`. Never `*ngIf`, `*ngFor`, `*ngSwitch`.

## Copying objects

In production `src/main/webapp/app/**/*.ts`, use the wrappers in
`src/main/webapp/app/foundation/util/deep-clone.util.ts`:

- `deepClone(x)` detaches nested state while preserving supported prototypes.
- `cloneWith(x, { a, b })` deep-clones the source and applies overrides by reference.
- `hydrate(new Course(), dto)` gives a parsed DTO its prototype.

`rules/prefer-deep-clone.mjs` bans object spread, `Object.assign` and `structuredClone` in that
scope, even for plain objects; specs are exempt. `eslint.config.mjs` also restricts direct lodash
cloning imports. Array spread and object rest remain allowed.

Shallow copies share nested state; `structuredClone` loses custom prototypes such as `dayjs`.
Do not clone merely to notify a signal if nested identity must survive. For that case and the
child-input identity boundary, read the cloning section of `reference/migration-recipes.md`.

`packages/tum-ui` is outside these application rules and must not import `app/` utilities.
Choose copying behavior appropriate to the package's data and identity requirements.

## Styling

Use TUM UI components (`@tumaet/ui-angular`) and Tailwind v4 utilities. Do not add Bootstrap or
ng-bootstrap in new work.

Colours use semantic tokens. Use TUM UI component variants, or `text-state-danger`,
`text-state-success`, `text-state-warning`, `text-state-info` for plain markup. Never `--p-<color>-N`
primitives, never `text-red-500`, never `text-danger`, never the superseded arbitrary
`text-(--danger)` form.

`localRules/no-raw-tailwind-color-palette` enforces the palette part across
`src/main/webapp/app/**/*.html` and `packages/tum-ui/src/lib/**/*.html`. **The Bootstrap ban is
only partly enforced**: `localRules/no-bootstrap-classes` covers the migrated directories listed
in `eslint.config.mjs`.
The convention applies throughout the client even where lint does not enforce it. Add newly
migrated directories to that list.

Never hand-write PrimeNG root classes such as `class="p-button"` or `class="p-inputtext"`. Render
the real PrimeNG component so its styles load deterministically. Enforced by
`localRules/no-primeng-component-classes`.

PrimeNG itself is a transitional fallback, used only when a TUM UI gap cannot reasonably be closed
in the same change. Explain the contained fallback in the pull request.

If TUM UI lacks a reusable capability, add or evolve a package component around native HTML or
stable Angular CDK primitives, and keep Artemis-specific composition in the application. See
`documentation/docs/developer/guidelines/tum-ui-kit.mdx`.

## Other rules worth knowing

Prefer `undefined` over `null`. Aim for full type safety; `localRules/no-as-any-cast` and
`localRules/no-as-unknown-cast` block the usual escape hatches. Filenames are kebab-case.

Full guidance: `documentation/docs/developer/guidelines/client-development.mdx`.
