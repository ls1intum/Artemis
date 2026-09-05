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
`Object.assign`, or `structuredClone`.

**Where it is enforced: `src/main/webapp/app/**/*.ts`, spec files exempt.** That boundary is set
twice, by the `files:` scope in `eslint.config.mjs` and again inside `rules/prefer-deep-clone.mjs`,
which registers no visitors unless the path contains `src/main/webapp/`.

**Within that scope the ban is unconditional, not a judgement call.**
`localRules/prefer-deep-clone` flags every object spread, `Object.assign` and `structuredClone`
there. It does not inspect what the value holds, so `{ ...{ a: 1 } }` fails lint exactly like a
spread of a `Course`. Do not reach for a spread because the object "looks plain".

The reasoning behind it is about entity-like values, which is where the silent corruption happens:

- `structuredClone()` is the worst option. It does not preserve prototypes, so a cloned `dayjs`
  date comes back as a plain object with no methods, while `dayjs.isDayjs()` still returns `true`,
  so no guard catches it.
- Spread and `Object.assign` copy one level. Nested objects stay shared, so a later edit mutates
  both. A non-empty `Object.assign` target is mutated in place, which emits no signal notification
  because a signal compares with `Object.is`.

Two companions live in the same file: `cloneWith(x, { a, b })` replaces `{ ...x, a, b }`, and
`hydrate(new Course(), dto)` replaces `Object.assign(new Course(), dto)` for giving a parsed server
DTO its prototype.

Reaching for lodash directly is blocked too, over the same scope: `eslint.config.mjs` forbids
importing `cloneDeep` and `cloneDeepWith` from `lodash-es`, and the `lodash-es/cloneDeep` subpath,
so all copying goes through the wrappers.

**`packages/tum-ui` is outside both.** Neither the rule nor the lodash restriction fires there, and
the package is standalone: it imports nothing from `app/`, so `deepClone` is not reachable from it
either. Nothing enforces this section inside the kit. The hazards are unchanged though, so a
component that copies a `dayjs` date or a nested object still needs a deep copy; it just has to
bring its own rather than reach across the package boundary.

**Array spread and object rest stay legal.** The rule does not touch them:
`items.update((items) => [...items, newItem])` is the documented way to append immutably, and
`const { a, ...rest } = post` is fine.

The signal interaction is subtle and is the part people get wrong. See the cloning section of
`reference/migration-recipes.md`.

## Styling

Use TUM UI components (`@tumaet/ui-angular`) and Tailwind v4 utilities. Do not add Bootstrap or
ng-bootstrap in new work.

Colours use semantic tokens. Use TUM UI component variants, or `text-state-danger`,
`text-state-success`, `text-state-warning`, `text-state-info` for plain markup. Never `--p-<color>-N`
primitives, never `text-red-500`, never `text-danger`, never the superseded arbitrary
`text-(--danger)` form.

`localRules/no-raw-tailwind-color-palette` enforces the palette part across
`src/main/webapp/app/**/*.html` and `packages/tum-ui/src/lib/**/*.html`. **The Bootstrap ban is only partly enforced**:
`localRules/no-bootstrap-classes` runs on an explicit allow-list of roughly two dozen already
migrated directories in `eslint.config.mjs`, not on the whole client. Lint passing is therefore not
evidence that a Bootstrap class is acceptable in an unmigrated area; the convention still applies
everywhere, the rule has just not caught up. If you migrate a directory, add it to that list.

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
