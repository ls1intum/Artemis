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

Every component must inherit valid CSS color values for these required custom properties:

- `--tum-ui-primary`
- `--tum-ui-primary-contrast`
- `--tum-ui-border-color`
- `--tum-ui-text-color`
- `--tum-ui-muted-color`
- `--tum-ui-highlight-color`, `--tum-ui-highlight-background`,
  `--tum-ui-highlight-focus-background`
- `--tum-ui-focus-ring-offset-background`
- `--tum-ui-state-danger`, `--tum-ui-state-success`, `--tum-ui-state-warning`,
  `--tum-ui-state-info`
- `--tum-ui-surface-{0,50,100,200,300,400,500,600,700,800,900,950}`

Surface tokens are a fixed ramp: `0` is always the lightest surface and `950` the darkest,
independent of the active theme. `primary-contrast` must remain readable on `primary`. Text, muted,
border, highlight, focus-offset, and state tokens describe the active theme and must update when
the theme changes. The package does not provide fallback values.

The stylesheet uses `tum:`-prefixed Tailwind class names to avoid unprefixed utility-selector
collisions and does not depend on the host's Tailwind build. Dark variants activate below an
ancestor with `data-theme="dark"`; hosts must set that attribute when dark mode is active.

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
```

`pnpm start` builds the package, watches it, and serves Artemis. An Artemis-only edit uses Angular
HMR. A package-source edit rebuilds the package and reloads the application.

Add implementation and focused behavior tests under `src/lib`. Export supported contracts
explicitly from `src/public-api.ts`. Package code must not import Artemis, PrimeNG, Bootstrap, or
ng-bootstrap; ESLint enforces the boundary.

Runtime and peer dependency versions are exact and synchronized with the workspace catalog.
Tailwind and PostCSS are build-only development dependencies pinned through the same catalog.
`rules/tum-ui-package.spec.mjs` rejects drift.

## License

MIT
