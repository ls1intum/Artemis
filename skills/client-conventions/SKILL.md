---
name: client-conventions
description: Apply Artemis conventions when changing Angular application or TUM AET UI code, migrating components, or fixing client lint violations.
---

# Artemis client conventions

Use `reference/migration-recipes.md` for migration examples. Check changes with `pnpm run lint`
and `pnpm run prettier:check`. Prefer standalone components.

## Signals are mandatory for new code

Use `input()` / `input.required()`, `output()`, `viewChild()` / `viewChild.required()`,
`viewChildren()`, `signal()`, `computed()`, `effect()`, and `inject()` for dependency injection.

The legacy decorators `@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, and
`@ContentChildren` are banned throughout the application, including co-located specs and test
helpers. `localRules/enforce-signal-apis` (`rules/enforce-signal-apis.mjs`) enforces this under
`src/main/webapp/app/` and `src/test/javascript/`. Use signal APIs when changing an existing
component; there is no unmigrated-module exception.

A `computed()`, `linkedSignal()`, `effect()` or `afterRenderEffect()` must read a signal, or it
never re-runs; a value that reads none is a constant and belongs in a plain `readonly` field
(`@angular-eslint/reactive-context-must-read-signal`).

## Injection and services

Declare every `inject()` field before any other class member (`@angular-eslint/inject-at-top`).
Fields initialize in declaration order, so a getter called from an earlier initializer would read
`undefined` from a service declared further down.

Declare an application-wide service with `@Service()`, not `@Injectable({ providedIn: 'root' })`
(`@angular-eslint/prefer-service-decorator`, autofixable). `@Service()` rejects constructor
injection and cannot share a class with another Angular decorator. The rule still reports a `@Pipe`
that is also injected as a service, and its autofix then breaks `ng build` with NG1006 (Vitest
compiles JIT and does not notice), so keep `@Injectable` on such a pipe with a justified line-level
disable and do not autofix it. Other provider metadata also keeps `@Injectable`.

## `ngOnChanges` is banned

Use `computed()` or `effect()`. Enforced at error level by
`localRules/prefer-signal-reactivity-over-ngonchanges` (`rules/prefer-signal-reactivity-over-ngonchanges.mjs`)
across `src/main/webapp/app`, `packages/tum-aet-ui/src/lib`, and `src/test/javascript`, including specs
and undecorated base classes.

This is a consistency ban, not a correctness fix. Angular does call inherited `ngOnChanges` hooks
and does fire them for signal inputs, so existing uses are not dead code.

A genuinely unavoidable case, meaning you need `SimpleChanges.previousValue` or `isFirstChange()`,
or ordering before child initialisation, needs a detailed comment and a justified line-level
`eslint-disable-next-line`. `ngOnInit` and `ngOnDestroy` are unaffected.

## Template control flow

Use `@if`, `@for`, `@switch`. Never `*ngIf`, `*ngFor`, `*ngSwitch`.

Every `@switch` has a `@default` (`@angular-eslint/template/require-switch-default`). Use
`@default never;` when the cases cover the whole union or enum, so the strict template check
reports a missing case, and an empty `@default {}` otherwise. Both render nothing for an unmatched
value.

Bind styles with `[style.prop]`, `[style.prop.unit]` or `[style]`, never `[ngStyle]`
(`@angular-eslint/template/prefer-style-binding`); a constant is a static `style` attribute. Never
bind `outerHTML` (`@angular-eslint/template/no-outerhtml`).

## Redirecting from guards and resolvers

A guard returns or emits `router.createUrlTree(...)`, or `new RedirectCommand(urlTree, options)`
when it needs `replaceUrl`, `skipLocationChange` or `state`. This also applies inside RxJS and
promise callbacks: return the redirect rather than throw it. The guards in one `canActivate`
array run together, and the first emitted result that is not `true`, in array order, wins.
A returned or emitted redirect waits for every guard ahead of it to return `true`; a thrown
`RedirectCommand` bypasses that ordering and can redirect before an earlier authority check
rejects the route.

A resolver returns or emits a `RedirectCommand`; a `UrlTree` returned from a resolver becomes
route data and does not redirect. Inside a resolver's RxJS operator or promise callback, it can
throw the `RedirectCommand` instead. The router cancels the running navigation with a redirect
that keeps its `replaceUrl` and `skipLocationChange`, and alerts shown before the throw still
appear.

Never call `router.navigate()` or `navigateByUrl()` in a guard or resolver. It cancels the running
navigation on the spot and starts a new one, so the original `replaceUrl` and
`skipLocationChange` are lost (Back then redirects forward again), a caller awaiting the original
navigation receives `false`, and the navigation still happens when another guard rejects the
route. A `return false` or `EMPTY` after the call changes nothing.

`localRules/no-navigation-in-guard-or-resolver` (`rules/no-navigation-in-guard-or-resolver.mjs`)
enforces this at error level under `src/main/webapp`. It follows the guard into nested callbacks,
into methods of its own class reached through `this`, and into functions of the same file. It is
file-local and does not resolve types, so it misses navigation in an injected service the guard
calls, a `Router` from a base-class field, `const router = this.router` or `injector.get(Router)`,
namespace imports, static helper calls and route objects without a marker key such as `path`.
Moving the call into a service silences the rule without fixing anything. A `catchError` after a
thrown redirect must rethrow what it does not handle.

In specs, use the real router (`TestBed.inject(Router)`, no `MockRouter`) and assert the result:
`router.serializeUrl(result as UrlTree)` for a returned redirect, or an `error` callback that
receives a `RedirectCommand` for a thrown one. Do not assert a `navigate` spy. For guard
combinations and browser history, route with `provideRouter(...)` and `provideLocationMocks()`
as in `src/main/webapp/app/localci/shared/localci-guard.spec.ts`.

## Form labels

Associate each visible form label with its native input using a matching `for` and `id`, or wrap
the input in the label. For a custom control, connect the label to the input inside the component,
not its host element. Use a heading or `span` for informational text that does not label a control;
give groups of controls an accessible group name. The
`@angular-eslint/template/label-has-associated-control` rule enforces this in TUM AET UI and the
client template areas listed in `eslint.config.mjs`.

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

`packages/tum-aet-ui` is outside these application rules and must not import `app/` utilities.
Choose copying behavior appropriate to the package's data and identity requirements.

## Styling

Use TUM AET UI components (`@tumaet/ui-angular`) and Tailwind v4 utilities. Do not add Bootstrap or
ng-bootstrap in new work.

Colours use semantic tokens. Use TUM AET UI component variants, or `text-state-danger`,
`text-state-success`, `text-state-warning`, `text-state-info` for plain markup. Never `--p-<color>-N`
primitives, never `text-red-500`, never `text-danger`, never the superseded arbitrary
`text-(--danger)` form.

`localRules/no-raw-tailwind-color-palette` enforces the palette part across
`src/main/webapp/app/**/*.html` and `packages/tum-aet-ui/src/lib/**/*.html`. **The Bootstrap ban is
only partly enforced**: `localRules/no-bootstrap-classes` covers the migrated directories listed
in `eslint.config.mjs`.
The convention applies throughout the client even where lint does not enforce it. Add newly
migrated directories to that list.

Never hand-write PrimeNG root classes such as `class="p-button"` or `class="p-inputtext"`. Render
the real PrimeNG component so its styles load deterministically. Enforced by
`localRules/no-primeng-component-classes`.

PrimeNG itself is a transitional fallback, used only when a TUM AET UI gap cannot reasonably be closed
in the same change. Explain the contained fallback in the pull request.

If TUM AET UI lacks a reusable capability, add or evolve a package component around native HTML,
Angular Aria (`@angular/aria`, for composite widgets such as menus and tabs), or stable Angular CDK
primitives, and keep Artemis-specific composition in the application. See
`documentation/docs/developer/guidelines/tum-aet-ui-kit.mdx`.

## Other rules worth knowing

Prefer `undefined` over `null`. Aim for full type safety; `localRules/no-as-any-cast` and
`localRules/no-as-unknown-cast` block the usual escape hatches. Filenames are kebab-case.

Full guidance: `documentation/docs/developer/guidelines/client-development.mdx`.
