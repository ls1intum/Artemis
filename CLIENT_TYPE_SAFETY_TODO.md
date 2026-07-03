# Client type-safety roadmap (working note — intentionally uncommitted)

> This file is deliberately **not committed** and **not gitignored**, so it keeps showing up in `git status`
> as a reminder. Delete it when the roadmap is done, or commit it into the docs if you want it to stick.

Goal: make production client code (`src/main/webapp`, specs excluded) as close to 100% compile-safe as
practical, each step enforced by ESLint (or tsconfig). Counts are violations measured on `develop`
(prod, excl specs) — they shift as earlier steps land.

## Done
- [x] `no-as-unknown-cast` (local rule) — bans `as unknown` / `as unknown as T` (#13052)
- [x] `no-as-any-cast` (local rule) — bans `as any` / `<any>` (#13060)
- [x] `JSON.parse` → `parseJson<T>()` everywhere + `no-restricted-properties` ban at error (#13059, #13060)
- [x] `restrict-template-expressions` → error (this PR) — no `any`/object/nullish/array in `` `${…}` ``
- [x] `ban-ts-comment` → error (#13062) — `@ts-ignore` banned, `@ts-expect-error` needs a description
- [x] `@typescript-eslint/no-explicit-any` → error (#13072) — 396 sites fixed across every module; 0 repo-wide.
- [x] `@typescript-eslint/no-unnecessary-type-assertion` → error (#13072) — 611 redundant casts removed (`--fix`).
- [x] `@typescript-eslint/consistent-type-assertions` `{ objectLiteralTypeAssertions: 'never' }` → error (#13072) — 56 `{…} as T` → `satisfies`/annotation.

## Tier 1 — cheap ratchets + diagnostics — DONE (#13090)
`strict` was **false** (only `noImplicitAny` + `strictNullChecks` on individually); enabled the cheap sub-flags:
- [x] `strictBindCallApply` → on — 0 prod fallout (spec-side `.call/.apply` dynamic-dispatch helpers fixed).
- [x] `noImplicitThis` → on — 42 sites: `function(){…this…}, this` callbacks → arrows.
- [x] `useUnknownInCatchVariables` → on — 55 sites: `instanceof` narrowing / `onError` / new `getErrorMessage(unknown)`
      helper. `onError` widened to accept `unknown`.
- [x] `no-console` (error, prod) — 10 `console.*` → Sentry `captureException`. Plus `no-restricted-globals`
      banning `globalThis` (prod-only, separate rule from the Monaco `no-restricted-syntax` block); 5 sites fixed
      (`globalThis.console.*` → Sentry, other → `window`). NOTE: this landed alongside Tier 1, not later.

## Tier 2 — correctness (recommended NEXT — planned)
- [ ] `@typescript-eslint/no-floating-promises` — **320** (196 files, every module); currently `off`.
      Re-measured 2026-07-03. Pattern breakdown: **~209 (65%) are `this.router.navigate(...)`** (fire-and-forget
      → `void`, behavior-preserving), ~36 `this.<svc>.<method>()` calls, ~4 `firstValueFrom/lastValueFrom`,
      ~71 other. Plan: enable `['error', { ignoreVoid: true, ignoreIIFE: true }]` **prod-only** (specs excluded —
      tests intentionally float promises). Fix strategy: `void` the fire-and-forget navigation and any genuinely
      ignored call (behavior-preserving); `await` (in async fns, with try/catch → `onError`) or `.catch(...)` only
      where a rejection should actually surface. Verify with the **FULL client suite + E2E** (behavioral rule).
      Risk low–medium (void is neutral; await changes timing — use sparingly). Branch off develop after #13090 merges.

## Tier 4 — the `no-unsafe-*` family (residual `any`-flow symptoms; smallest-first, after Tier 1)
`no-explicit-any` (the source) landed in #13072, so these dropped from their pre-campaign highs. What remains
leaks in from DOM / third-party / untyped boundaries; `useUnknownInCatchVariables` (Tier 1) removes the
error-handler chunk first. Re-measured 2026-07-03:
- [ ] `@typescript-eslint/no-unsafe-return` — 197 (104 files)
- [ ] `@typescript-eslint/no-unsafe-call` — 179 (74 files)
- [ ] `@typescript-eslint/no-unsafe-argument` — 664 (257 files)
- [ ] `@typescript-eslint/no-unsafe-member-access` — 704 (197 files)
- [ ] `@typescript-eslint/no-unsafe-assignment` — 716 (273 files)

## Tier 3 — function soundness → `strict: true` (fallout measured 2026-07-03 on tsconfig.app.json)
- [ ] `strictFunctionTypes` → on — **147**. Catches unsound function-parameter variance.
- [ ] `strict: true` umbrella — total **1310**, dominated by `strictPropertyInitialization` (**1064**, mostly
      Angular fields; note it ADDS `!`, in tension with `no-non-null-assertion`). Enable the Tier 1 sub-flags
      first, tackle property-init as its own campaign, then flip `strict`. (`strictBindCallApply`=0 already.)

## Tier 5 — big-ticket (high value, large; each its own multi-PR campaign)
- [ ] `noUncheckedIndexedAccess` — **899**. Highest-impact for real bugs: `arr[i]`/`obj[key]` → `T | undefined`;
      catches a huge class of "undefined is not…" errors.
- [ ] `strictPropertyInitialization` — **1064** (see Tier 3).

## Lower priority / not recommended soon
- [ ] `@typescript-eslint/no-non-null-assertion` — **3468** (542 files). #13072 trimmed ~300 redundant ones.
      `!` is explicit and less dangerous than `any`; in tension with `strictPropertyInitialization`. Long tail.
- [ ] `@typescript-eslint/no-unnecessary-condition` — **2212** (553 files). Noisy — many are intentional
      defensive checks; flags always-true/false conditions. Mixed ROI.
- [ ] `exactOptionalPropertyTypes` — **1806**. Subtle (`{x?: T}` vs `{x: T | undefined}`); lowest ROI/effort.

Notes on the globalThis regression ban (to bundle with `no-console`): prod is already globalThis-free (the 4
prod usages were removed earlier), so this is a **prevent-regression** guard only — prod-only (specs use it
~43× legitimately), and it must coexist with the existing Monaco `no-restricted-syntax` block.

## Explicitly NOT recommended for the compile-safety track
- Forbidding the **object spread** operator (`{...x}`, ~191 sites) — spread is fully type-checked in TS; banning
  it is a style/immutability choice (that's what CLAUDE.md's "avoid object spread" is about), **zero** type-safety
  gain. If desired, enforce as a separate `no-restricted-syntax` rule, not part of this track.
- Forbidding **`Object.assign`** (124 sites) — also type-checked; occasionally a mutation smell, but not a
  compile-safety lever.

## Loose ends
- `virtual-scroll.component.ts:239` has the only hand-written `@ts-ignore` (paired with an inline
  `eslint-disable ban-ts-comment`), for `scrollTo({ behavior: 'instant' })`. `ScrollBehavior` now includes
  `'instant'` in TS 5.9's lib.dom — this suppression is likely **obsolete** and could be removed outright.
- The 48 `@ts-ignore` in `app/openapi/**` are in generated code that ESLint already ignores — leave them.
