# @tumaet/ui-angular

Reusable Angular components and precompiled styles for the TUM UI design system, currently
maintained in the Artemis workspace. The built artifact exposes package-owned APIs and assets;
application-specific integration remains in the host.

## Consumer setup

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

The stylesheet includes complete light and dark defaults in a low-priority cascade layer. Set
`data-theme="dark"` on the document element to activate dark mode. The theme also sets the matching
`color-scheme` and a system font stack. No Tailwind dependency, configuration, or package source
scanning is required.

## Host theme integration

Override only the semantic roles the application owns in an unlayered host stylesheet. Define
overrides on the document element so package components and overlay content inherit them. The
package defaults remain as fallbacks because unlayered application declarations take precedence
over the package theme layer. Set `data-theme` and the standard `color-scheme` property on the same
root so package and browser-owned controls use the same scheme.

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

Color values must remain valid for each active color scheme. The default palette uses a cool-slate
surface hierarchy with restrained control boundaries and visible focus states. Contrast tokens must
remain readable on their matching background. Primary is the brand fill; accent is the accessible brand foreground for
content and controls. Each state token is a solid fill or border, its `-contrast` token is text on
that fill, and its `-foreground` token is text on content or a tinted state surface. Focus must remain
distinguishable from adjacent content and control surfaces. The package exposes semantic roles
rather than a numbered color ramp. Artemis inherits the default foundations and surfaces, then
overrides its font family, brand, application surfaces, and status roles in its own stylesheet.

The component stylesheet uses `tum:`-prefixed Tailwind class names to avoid selector collisions and
does not depend on the host's source scanner. The prefix and package Tailwind configuration are
internal implementation details. Theme changes flow through the custom properties, and package
components do not contain light/dark palette branches. Responsive component thresholds are
package-owned compile-time values at 40rem, 48rem, 64rem, 80rem, and 96rem; they do not follow a
host's Tailwind breakpoints.

Apply host-owned layout classes with the native `class` attribute on a package component. Those
classes style the component host; they do not cause the package Tailwind build to generate
utilities. Use component inputs and theme tokens for supported internal customization instead of
targeting implementation elements.

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
