# Client test reference

## Invocation

```bash
pnpm exec vitest run <path/to/spec.ts>   # a single file
pnpm run vitest                          # watch mode
pnpm run vitest:run                      # the whole suite
pnpm run test-diff                       # only specs affected by the diff against develop
pnpm run compile:tests                   # the strict spec tsc that CI runs
```

`pnpm run vitest:run -- <path>` does **not** filter. The argument is swallowed and the whole suite
runs.

## The type check CI runs is stricter than Vitest

`pnpm run compile:tests` type-checks against `tsconfig.spec.json` and enforces member visibility.
Vitest does not. A spec reaching a private member directly compiles under Vitest and fails in CI.

```typescript
// fails compile:tests
expect(component.privateHelper).toBeDefined();

// passes both
expect(component['privateHelper']).toBeDefined();
```

Run `compile:tests` before pushing any spec change.

## Signal inputs in specs

A component using `input()` is driven in a spec through the component ref, not by assigning a
field. A `model()` is a writable signal plus the matching change output that `[(name)]` binds to.
An `input()` plus an `output()` can preserve that binding, but only if the output is named
`<input>Change` and is actually emitted on every write. Miss either and the parent silently stops
receiving updates, which a spec driving the child directly will not catch. Prefer `model()`.

`MockProvider` does not stub a signal that a service initialises as a field. If a component reads a
shared signal from a service, provide the real service or an explicit stub object; a `MockProvider`
gives back `undefined` and the failure is confusing.

## Zoneless change detection

The client runs zoneless. A plain field that gates an `@if` will not trigger a re-render when it
changes after an async load: the template never re-evaluates. Make the guard a `signal()`.

This is the most common cause of a component that renders correctly in the browser during
development but shows an empty template in a spec after an awaited call, or the reverse.

## Monaco

Monaco is stubbed under Vitest. A spec cannot exercise real editor behaviour. There is a separate
configuration, `vitest.monaco.config.ts`, run by `pnpm run vitest:monaco`, for the specs that need
the real thing.

## Template errors

Neither Vitest nor `compile:tests` catches every template error. A structurally changed template
needs a real build:

```bash
pnpm run webapp:prod
```

## Stray compiled JavaScript

If a large number of specs suddenly fail with errors about reading a property of `undefined` on
what should be an enum, look for compiled `.js` files sitting next to their `.ts` sources. They are
gitignored, so they are invisible in `git status`, and they shadow the TypeScript. CI is unaffected,
which makes it look like a local-only mystery. Delete them.

## Committing

A pre-commit hook formats staged files. Do not commit while a background process is still editing
the tree, or the hook will format a half-written state. Verify what was committed by reading the
content, not by trusting an exit code.
