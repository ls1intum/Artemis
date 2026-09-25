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

## Directives: `TestBed.createDirective`

Since Angular 22.2, TestBed creates the host element itself and applies the directive to it. Do not
declare a host `@Component` just to put the directive on an element.

```typescript
import { inputBinding, outputBinding, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

const text = signal<string | undefined>('# Title');
let renderCount = 0;
const fixture = TestBed.createDirective(MarkdownDirective, {
    tagName: 'div', // the selector [jhiMarkdown] names no element
    bindings: [inputBinding('jhiMarkdown', text), outputBinding('markdownRendered', () => renderCount++)],
});
fixture.detectChanges(); // applies the input bindings
// Assert on fixture.nativeElement (the host) and fixture.directiveInstance; text.set(...) drives the input.
```

- `tagName` is required when the selector names no element (`[jhiMarkdown]`) or two different
  tags, or `createDirective` throws. `table[tumUiTable]` infers `<table>` on its own.
- `inputBinding(name, signalOrGetter)`, `outputBinding(name, handler)` and
  `twoWayBinding(name, writableSignal)` (for a `model()`) come from `@angular/core`;
  `DirectiveFixture` comes from `@angular/core/testing`. Use the public name, which is the alias
  where one exists: `tumUiTooltip`, not `content`. A wrong name throws `NG0315` in dev mode.
- **Input bindings are applied on the first `fixture.detectChanges()`.** Before that an input
  holds its default and a required input throws when read. Output bindings are live at once. After
  that, a changed signal reaches the directive on the next `fixture.detectChanges()`.
- Call `TestBed.configureTestingModule({ providers })` before `createDirective`. No
  `compileComponents()` is needed.
- The host starts empty, directly under `<body>`, and the directive's constructor has already run by
  the time `createDirective` returns. A template is no different: Angular creates a directive before
  the child elements of its host, so a constructor never sees children. Content the directive reads
  in `ngOnInit` or later can be appended to `fixture.nativeElement` before the first
  `detectChanges()`.
- `fixture.debugElement` is the host itself, and it hosts an internal empty component, so
  `debugElement.query()` does **not** find elements inside it. Use
  `fixture.nativeElement.querySelector()`.

Keep a host component when:

- the directive is structural and injects `TemplateRef` (`*jhiHasAnyAuthority`,
  `*jhiExtensionPoint`, the title bar directives);
- it needs something around or beside it, or content Angular keeps rendering inside it: `jhiSortBy`
  needs `jhiSort` on an enclosing element and reads its projected `fa-icon`, a validator tested
  through `ngModel` needs the `NgModel`, `infinite-scroll` must keep its sentinels around a `@for`
  list;
- its constructor reads a static attribute the template writes on the host (`jhiSecureLink` must
  leave an existing `href` alone). Angular writes static attributes before it creates the
  directive; `createDirective` cannot;
- an input takes a `TemplateRef` or a component instance from the same template
  (`jhiStickyPopover`, `tumUiPopoverTrigger`).

An abstract base directive is still tested through a concrete subclass. One spec can mix both
styles: `src/main/webapp/app/assessment/manage/secure-link.directive.spec.ts` uses
`createDirective` and keeps one small host, with a comment saying why, for the `href` case.

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
