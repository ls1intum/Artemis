# @tumaet/ui-angular

Internal Angular component package maintained in the Artemis workspace and designed for eventual
independent publication.

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
ramp.

Applications without an existing token system can load the optional reference themes before the
component stylesheet:

```json
{
    "styles": ["@tumaet/ui-angular/themes.css", "@tumaet/ui-angular/styles.css"]
}
```

Set `data-theme="dark"` on the document element to activate the dark reference theme. Artemis maps
the same contract to its own design tokens and therefore does not load this optional file.

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
signals keep translations and locale-sensitive formatting reactive. Register one translator
adapter when the application starts.

## Contributing

Contributors working in the Artemis repository should follow the
[TUM UI package guide](https://docs.artemis.tum.de/developer/guidelines/tum-ui-kit). It defines
the ownership boundary, supported workflow, testing expectations, and Storybook conventions.

## License

MIT
