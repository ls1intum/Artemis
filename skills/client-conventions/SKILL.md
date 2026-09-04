---
name: client-conventions
description: Write Angular code for Artemis that passes lint and review the first time. Use when creating or changing anything under src/main/webapp/app or packages/tum-ui, when an ESLint localRules check fails, or when migrating a component to signals. Covers signal APIs, the ngOnChanges ban, template control flow, object cloning, and the TUM UI and Tailwind styling rules.
---

# Artemis client conventions

These are enforced, not advisory. Most have a custom ESLint rule in `rules/` behind them, so
breaking one fails Client Code Style rather than merely attracting a review comment.

Verify with:

```bash
pnpm run lint
pnpm run prettier:check
```

`reference/migration-recipes.md` has the before-and-after for each migration. Read it when changing
existing code rather than inventing a translation.

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

Use `deepClone` from `src/main/webapp/app/foundation/util/deep-clone.util.ts`. Never object spread,
`Object.assign`, or `structuredClone`, for anything entity-like: anything that may hold a `dayjs`
date, a nested object, a `Map` or `Set`, or a circular reference.

- `structuredClone()` is the worst option. It does not preserve prototypes, so a cloned `dayjs`
  date comes back as a plain object with no methods.
- Spread and `Object.assign` copy one level. Nested objects stay shared, so a later edit mutates
  both.

Two companions live in the same file: `cloneWith(x, { a, b })` replaces `{ ...x, a, b }`, and
`hydrate(new Course(), dto)` replaces `Object.assign(new Course(), dto)` for giving a parsed server
DTO its prototype.

Enforced by `localRules/prefer-deep-clone` (`rules/prefer-deep-clone.mjs`), production client
TypeScript only, specs exempt. Importing `cloneDeep` from `lodash-es` is blocked so all copying
goes through the wrappers.

Array spread stays fine: `items.update((items) => [...items, newItem])` is the documented way to
append immutably. Object rest in destructuring is fine too.

The signal interaction is subtle and is the part people get wrong. See the cloning section of
`reference/migration-recipes.md`.

## Styling

Use TUM UI components (`@tumaet/ui-angular`) and Tailwind v4 utilities. Do not add Bootstrap or
ng-bootstrap in new work.

Colours use semantic tokens. Use TUM UI component variants, or `text-state-danger`,
`text-state-success`, `text-state-warning`, `text-state-info` for plain markup. Never `--p-<color>-N`
primitives, never `text-red-500`, never `text-danger`, never the superseded arbitrary
`text-(--danger)` form. Enforced by `localRules/no-raw-tailwind-color-palette` and
`localRules/no-bootstrap-classes`.

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
