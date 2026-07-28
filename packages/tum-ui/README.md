# @tumaet/ui-angular

Reusable Angular UI components for TUM applications. The package is built with ng-packagr, follows
the Angular Package Format, and is consumed by Artemis through its public package entry point.

The package is currently developed inside the Artemis workspace. Its application-independent API,
dependencies, styles, and translations are separated so it can move to its own repository later
without changing Artemis imports.

## Use in an Angular application

Import public symbols only from the package root:

```ts
import { TumUiButtonComponent, TumUiDialogComponent } from '@tumaet/ui-angular';
```

Import the package's Tailwind CSS v4 entrypoint from the host's Tailwind stylesheet:

```css
@import '@tumaet/ui-angular/theme.css';
```

It scans the compiled package and registers namespaced color utilities. The host must define the
`--tum-ui-primary`, `--tum-ui-border-color`, `--tum-ui-text-color`, `--tum-ui-muted-color`,
`--tum-ui-state-*`, `--tum-ui-surface-{0,50,...,950}`, and
`--tum-ui-focus-ring-offset-background` custom properties. This keeps the package independent of a
particular host theme while allowing each application to map its own tokens.

Package-owned text has English defaults. A translated host can provide an adapter at bootstrap:

```ts
import { provideTumUiTranslator } from '@tumaet/ui-angular';

bootstrapApplication(AppComponent, {
    providers: [provideTumUiTranslator(ApplicationTranslator)],
});
```

`ApplicationTranslator` implements `TumUiTranslator` and can inject the host's translation service.
Artemis keeps this and other host-specific adapters under `app/shared-ui/tum-ui-integration`.

## Development in Artemis

- `pnpm run tum-ui:build` builds the production package into `dist/tum-ui`.
- `pnpm run tum-ui:build:watch` rebuilds it incrementally.
- `pnpm run tum-ui:test` runs the package specs with the repository's Vitest setup.
- `pnpm run tum-ui:pack:check` validates the packed Angular Package Format artifact with publint and
  AreTheTypesWrong.

The normal Artemis build and test commands build the package first. `pnpm start` watches both the
package and application. Application-only edits retain Angular HMR; a package edit rebuilds the
library incrementally and currently triggers an Artemis page reload. A standalone Storybook is the
appropriate follow-up for a faster isolated component loop.

Add components under `src/lib`, colocate meaningful behavior tests, and export only supported
contracts from `src/public-api.ts`. Package code must not import Artemis, PrimeNG, Bootstrap, or
ng-bootstrap; ESLint enforces this boundary.

Runtime and peer versions are pinned in this manifest because ng-packagr copies them into the
artifact. `pnpm-workspace.yaml` is the canonical version catalog, and the rule suite fails if the
package pins drift.

See the
[Artemis TUM UI guide](../../documentation/docs/developer/guidelines/tum-ui-kit.mdx)
for component APIs, design conventions, and the migration roadmap.

## License

MIT
