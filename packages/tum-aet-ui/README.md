# TUM AET UI

Angular components and precompiled styles from the [Applied Education Technologies (AET)
research group at the Technical University of Munich](https://aet.cit.tum.de/). The library is
published as [`@tumaet/ui-angular`](https://www.npmjs.com/package/@tumaet/ui-angular). See the
[component reference](https://docs.artemis.tum.de/developer/tum-aet-ui-reference) for examples and APIs.

## Getting started

Install in an Angular application whose dependencies satisfy the package's `peerDependencies`:

```sh
npm install @tumaet/ui-angular
```

Use the Angular CLI or another build that runs Angular's linker. Zone.js is not required.
Server-side rendering is not supported.

In `angular.json`, add the stylesheet to `projects.<app>.architect.build.options.styles`, after
existing global styles. Keep your application's current `.css` or `.scss` path:

```json
{
    "styles": ["src/styles.scss", "@tumaet/ui-angular/styles.css"]
}
```

The package includes precompiled styles and overlay styles. No Tailwind setup or icon registration
is required for built-in controls.

For a minimal standalone application, use this `src/main.ts` with `<app-root></app-root>` in
`src/index.html`:

```ts
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { TumAetUiButtonComponent, TumAetUiDialogComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'app-root',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumAetUiButtonComponent, TumAetUiDialogComponent],
    template: `
        <tumaet-ui-button (clicked)="dialogOpen.set(true)">Open dialog</tumaet-ui-button>
        <tumaet-ui-dialog header="Welcome" [(visible)]="dialogOpen">
            <p>Your first TUM AET UI dialog.</p>
        </tumaet-ui-dialog>
    `,
})
class App {
    readonly dialogOpen = signal(false);
}

void bootstrapApplication(App);
```

In an existing application, add the components to the consuming component's `imports` instead of
replacing its bootstrap. Import supported symbols from `@tumaet/ui-angular`; deep imports are
not supported. See the [component reference](https://docs.artemis.tum.de/developer/tum-aet-ui-reference)
for forms, tables, charts, and keyboard interactions.

## Host theme integration

Theme defaults use a low-priority cascade layer. Set `data-theme="dark"` on `<html>` to activate
dark mode, or remove it to return to light mode. The theme sets the matching CSS `color-scheme`
and a system font stack.

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

## Icons

Built-in controls import individual solid icons through the official Font Awesome Angular
component. Applications do not need to register these icons or load an icon font.

For an `icon` input, prefer an explicit icon definition such as `faDownload` from
`@fortawesome/free-solid-svg-icons`, passed through `[icon]`. Declare any icon pack your own
application imports as its dependency. Explicit references support tree shaking and avoid
string-name lookup; see
[Font Awesome's explicit-reference guide](https://github.com/FortAwesome/angular-fontawesome/blob/main/docs/usage/explicit-reference.md).

## Translations

Package-owned text defaults to English. To connect an application's translation service,
implement `TumAetUiTranslator` and register `provideTumAetUiTranslator(YourTranslator)` in the
application's providers.

The adapter replaces the default translator. Its `translate(key, params)` method must resolve
package `tumAetUi.*` keys, application-owned keys passed to components, and supplied interpolation
parameters. Optional `translationChanges` and `locale` signals update translated text and
locale-sensitive formatting. See the
[translation contract](https://docs.artemis.tum.de/developer/guidelines/tum-aet-ui-kit#translation-contract).

## Releases and support

Releases use `@tumaet/ui-angular@<version>` tags. Check [GitHub Releases](https://github.com/ls1intum/Artemis/releases)
for version-specific changes and migrations. Before 1.0, incompatible changes increment the minor
version; compatible changes increment the patch version.

[Report an issue](https://github.com/ls1intum/Artemis/issues) with the package and Angular versions,
expected behavior, and a minimal reproduction.

## Contributing

Contributors working in the Artemis repository should follow the
[TUM AET UI package guide](https://docs.artemis.tum.de/developer/guidelines/tum-aet-ui-kit). It covers
contribution, testing, and publishing.

## License

[MIT](https://github.com/ls1intum/Artemis/blob/develop/packages/tum-aet-ui/LICENSE).
