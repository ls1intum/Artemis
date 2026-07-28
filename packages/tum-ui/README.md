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

Import the Tailwind CSS v4 entry point from the host's Tailwind stylesheet:

```css
@import '@tumaet/ui-angular/theme.css';
```

The host must provide these CSS custom properties:

- `--tum-ui-primary`
- `--tum-ui-border-color`
- `--tum-ui-text-color`
- `--tum-ui-muted-color`
- `--tum-ui-focus-ring-offset-background`
- `--tum-ui-state-danger`, `--tum-ui-state-success`, `--tum-ui-state-warning`,
  `--tum-ui-state-info`
- `--tum-ui-surface-{0,50,100,200,300,400,500,600,700,800,900,950}`

Components use Tailwind's `dark:` variant, so the host must align that variant with its theme
selector.

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

Dependency versions in `package.json` are exact because they are copied into the ng-packagr
artifact. `pnpm-workspace.yaml` owns the matching workspace catalog, and
`rules/tum-ui-package.spec.mjs` rejects drift.

## License

MIT
