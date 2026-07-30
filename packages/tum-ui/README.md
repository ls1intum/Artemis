# @tumaet/ui-angular

Internal Angular component package maintained in the Artemis workspace. The built artifact exposes
package-owned APIs and assets; Artemis-specific integration remains in the host.

The package contains reusable TUM UI code. It is not the default component library for new Artemis
UI; Artemis uses PrimeNG according to the repository's client-development guidelines.

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
    "styles": ["src/styles.scss", "@tumaet/ui-angular/styles.css"]
}
```

Every component must inherit valid values for the Artemis semantic custom properties:

- brand: `--artemis-primary-color`, `--artemis-primary-contrast-color`,
  `--artemis-accent-color`;
- text: `--artemis-text-color`, `--artemis-text-hover-color`, `--artemis-muted-color`,
  `--artemis-disabled-color`;
- backgrounds: `--artemis-content-background`, `--artemis-control-background`,
  `--artemis-overlay-background`, `--artemis-hover-background`, `--artemis-disabled-background`;
- borders: `--artemis-border-color`, `--artemis-control-border-color`,
  `--artemis-control-border-hover-color`;
- selection: `--artemis-highlight-color`, `--artemis-highlight-background`,
  `--artemis-highlight-focus-background`;
- states: `--artemis-state-{danger,success,warning,info}` and each corresponding
  `--artemis-state-*-contrast` and `--artemis-state-*-foreground`;
- specialized roles: `--artemis-contrast-background`, `--artemis-contrast-color`,
  `--artemis-table-striped-background`, `--artemis-tooltip-background`,
  `--artemis-tooltip-color`.

The values describe the active theme and must change with it. Contrast tokens must remain readable
on their matching background. Primary is the brand fill; accent is the accessible brand foreground
for content and controls. The package exposes semantic roles rather than a numbered color ramp.

Applications that do not provide the token contract can load the reference theme before the
component stylesheet:

```json
{
    "styles": ["@tumaet/ui-angular/themes.css", "@tumaet/ui-angular/styles.css"]
}
```

Set `data-theme="dark"` on the document element to activate the dark reference theme. Artemis maps
the same contract directly to its existing light and dark design tokens instead.

Tailwind hosts can also import `@tumaet/ui-angular/tailwind-theme.css` to expose the same semantic
tokens through their own unprefixed utilities.

The component stylesheet uses `tum:`-prefixed Tailwind class names to avoid selector collisions and
does not depend on the host's source scanner. The prefix is internal; semantic utility names after
it match the shared Tailwind theme. Theme changes flow through the custom properties, and package
components do not contain light/dark palette branches.

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

`ApplicationTranslator` must implement `TumUiTranslator`. Its optional `translationChanges` and
`locale` signals keep translations and locale-sensitive formatting reactive. Register one
translator adapter when the application starts.

## Contributing

Contributors working in the Artemis repository should follow the
[TUM UI package guide](https://docs.artemis.tum.de/developer/guidelines/tum-ui-kit). It defines
the ownership boundary, supported workflow, testing expectations, and Storybook conventions.

## License

MIT
