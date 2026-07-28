# @tumaet/ui-angular

Private Angular component package maintained in the Artemis workspace.

The package is an extraction boundary for reusable TUM UI code. It is not the default component
library for new Artemis UI; Artemis uses PrimeNG according to the repository's client-development
guidelines.

## Consumer setup

Import supported symbols from the package entry point:

```ts
import { TumUiButtonComponent, TumUiDialogComponent } from '@tumaet/ui-angular';
```

Deep imports are not supported.

Configure the host bundler to load the precompiled stylesheet once, globally, after resets and
framework styles. For an Angular CLI application:

```json
{
    "styles": ["src/styles.scss", "node_modules/@tumaet/ui-angular/styles.css"]
}
```

Every component must inherit valid values for the package's semantic custom properties:

- brand: `--tum-ui-primary`, `--tum-ui-primary-contrast`;
- text: `--tum-ui-text-color`, `--tum-ui-text-hover-color`, `--tum-ui-muted-color`,
  `--tum-ui-disabled-color`;
- backgrounds: `--tum-ui-content-background`, `--tum-ui-control-background`,
  `--tum-ui-overlay-background`, `--tum-ui-hover-background`, `--tum-ui-disabled-background`;
- borders: `--tum-ui-border-color`, `--tum-ui-control-border-color`,
  `--tum-ui-control-border-hover-color`;
- selection: `--tum-ui-highlight-color`, `--tum-ui-highlight-background`,
  `--tum-ui-highlight-focus-background`;
- states: `--tum-ui-state-{danger,success,warning,info}` and each corresponding
  `--tum-ui-state-*-contrast` and `--tum-ui-state-*-foreground`;
- specialized roles: `--tum-ui-contrast-background`, `--tum-ui-contrast-color`,
  `--tum-ui-table-striped-background`, `--tum-ui-tooltip-background`,
  `--tum-ui-tooltip-color`.

The values describe the active theme and must change with it. Contrast tokens must remain readable
on their matching background. The package deliberately exposes roles rather than a numbered color
ramp, and it does not provide fallback values.

The stylesheet uses `tum:`-prefixed Tailwind class names to avoid selector collisions and does not
depend on the host's Tailwind build or source scanner. Theme changes flow through the semantic
custom properties; package components do not contain light/dark palette branches.

`styleClass` inputs append classes already defined by the host; they do not cause the package
Tailwind build to generate utilities. Use them for non-conflicting layout hooks. Prefer component
inputs and theme tokens over overriding internal component styles.

Package text defaults to English. A translated host can replace it with an adapter:

```ts
import { provideTumUiTranslator } from '@tumaet/ui-angular';

bootstrapApplication(AppComponent, {
    providers: [provideTumUiTranslator(ApplicationTranslator)],
});
```

`ApplicationTranslator` must implement `TumUiTranslator`. Its optional `changes` and `locale`
signals keep translations and locale-sensitive formatting reactive. Artemis installs its adapter
in `app.config.ts`; consumers must not provide a second one.

## Development

Run commands from the Artemis repository root:

```bash
pnpm run tum-ui:build
pnpm run tum-ui:test
pnpm run tum-ui:stylelint
pnpm run tum-ui:pack:check
pnpm --dir packages/tum-ui run storybook
pnpm --dir packages/tum-ui run storybook:typecheck
pnpm --dir packages/tum-ui run storybook:test
pnpm --dir packages/tum-ui run storybook:build
pnpm --dir packages/tum-ui run storybook:docs:test
```

`pnpm start` builds the package, watches it, and serves Artemis. An Artemis-only edit uses Angular
HMR. A package-source edit rebuilds the package and reloads the application.

Add implementation and focused behavior tests under `src/lib`. Export supported contracts
explicitly from `src/public-api.ts`. Package code must not import Artemis, PrimeNG, Bootstrap, or
ng-bootstrap; ESLint enforces the boundary.

Runtime and peer dependency versions are exact and synchronized with the workspace catalog.
Tailwind and PostCSS are build-only development dependencies pinned through the same catalog.
`rules/tum-ui-package.spec.mjs` rejects drift.

Storybook configuration, stories, and their direct toolchain dependencies belong to this package.
Shared dependency versions stay synchronized with Artemis through the workspace catalog. Unit
tests and TypeScript configurations still use the workspace test runner and compiler policy while
the package lives in this repository. Stories use stable CSF3 with `Meta`, `StoryObj`, and
TypeScript's `satisfies` operator. The theme toolbar applies to component previews and AutoDocs;
the manager remains independently themed by Storybook. Every story runs in both package themes,
so theme-specific duplicate stories are unnecessary. Compodoc supplies AutoDocs API metadata;
JSDoc should explain only non-obvious public behavior or constraints, never repeat names, types,
or defaults. Storybook's Angular Vite integration is a preview feature, so its exact version stays
pinned and upgrades require HMR verification, the package build, Chromium story tests, the static
Storybook build, and the AutoDocs theme tests to pass.

## License

MIT
