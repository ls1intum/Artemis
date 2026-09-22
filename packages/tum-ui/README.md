# @tumaet/ui-angular

Angular components and precompiled styles for the TUM UI design system. See the
[component reference](https://docs.artemis.tum.de/developer/tum-ui-reference) for examples and APIs.

## Consumer setup

Install the package in your Angular application:

```sh
npm install @tumaet/ui-angular
```

The published `package.json` defines supported peer versions. Use the Angular CLI or another
build that runs Angular's linker. Zone.js is not required. Server-side rendering is not supported.

Import supported symbols from the package entry point:

```ts
import { TumUiButtonComponent, TumUiDialogComponent } from '@tumaet/ui-angular';
```

Deep imports are not supported.

Load the precompiled stylesheet once, globally, after resets and framework styles:

```json
{
    "styles": ["src/styles.scss", "@tumaet/ui-angular/styles.css"]
}
```

Theme defaults use a low-priority cascade layer. Set `data-theme="dark"` on the document element
to activate dark mode. The theme also sets the matching `color-scheme` and a system font stack.
No Tailwind dependency, configuration, or package source scanning is required.

## Host theme integration

Override semantic custom properties in an unlayered stylesheet on the document element so
components and overlays inherit them. Unlayered declarations take precedence over the package's
layered defaults. Supply light and dark values when overriding colors.

Foundations:

- spacing: `--tumaet-ui-spacing`;
- type: `--tumaet-ui-font-family`, `--tumaet-ui-font-size-{xs,sm,base,lg,xl}`, and the
  corresponding `--tumaet-ui-line-height-*` properties;
- shape: `--tumaet-ui-radius-{sm,md,xl,2xl}`;
- elevation: `--tumaet-ui-shadow-{xs,sm,md,lg,xl}`;
- focus: `--tumaet-ui-focus-color`.

Colors:

- brand: `--tumaet-ui-primary-color`, `--tumaet-ui-primary-contrast-color`,
  `--tumaet-ui-accent-color`;
- text: `--tumaet-ui-text-color`, `--tumaet-ui-text-hover-color`, `--tumaet-ui-muted-color`,
  `--tumaet-ui-disabled-color`;
- backgrounds: `--tumaet-ui-content-background`, `--tumaet-ui-control-background`,
  `--tumaet-ui-overlay-background`, `--tumaet-ui-hover-background`, `--tumaet-ui-disabled-background`;
- borders: `--tumaet-ui-border-color`, `--tumaet-ui-control-border-color`,
  `--tumaet-ui-control-border-hover-color`;
- selection: `--tumaet-ui-highlight-color`, `--tumaet-ui-highlight-background`,
  `--tumaet-ui-highlight-focus-background`;
- states: `--tumaet-ui-state-{danger,success,warning,info}` and each corresponding
  `--tumaet-ui-state-*-contrast` and `--tumaet-ui-state-*-foreground`;
- specialized roles: `--tumaet-ui-contrast-background`, `--tumaet-ui-contrast-color`,
  `--tumaet-ui-table-striped-background`, `--tumaet-ui-tooltip-background`,
  `--tumaet-ui-tooltip-color`.

Primary is the brand fill; accent is the brand foreground for content and controls. Each state
token is a fill or border, its `-contrast` token is text on that fill, and its `-foreground` token
is text on content or a tinted state surface. Maintain readable contrast in both themes and keep
focus indicators distinguishable from adjacent surfaces.

Responsive thresholds are fixed at 40rem, 48rem, 64rem, 80rem, and 96rem; host Tailwind
configuration does not change them.

Apply host-owned layout classes with the native `class` attribute on a package component. These
classes style the host element. Use component inputs and theme tokens to customize its contents.

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
[TUM UI package guide](https://docs.artemis.tum.de/developer/guidelines/tum-ui-kit). It covers
contribution, testing, and publishing.

## License

MIT
