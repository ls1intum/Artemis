/**
 * Maps Apollon's public `--apollon-*` theming contract onto Artemis's live PrimeNG
 * design tokens, so the embedded modeling editor adopts Artemis's primary colour,
 * surfaces, text and borders — in light **and** dark — and re-colours automatically
 * when the user toggles the theme.
 *
 * The values are `var(--p-*)` references resolved through the CSS cascade, so they are
 * stamped once and stay live with zero re-application: when Artemis flips the theme, the
 * PrimeNG tokens re-resolve and the editor re-colours for free.
 *
 * **Scope matters.** Apollon's floating chrome (element palette, zoom/undo controls,
 * minimap, rails) is a `color-mix()` ramp derived from `--apollon-background` +
 * `--apollon-primary-contrast`, and that ramp is declared once at `:root`. So the two
 * base tokens must be themed on the **document root** for the chrome to follow — theming
 * them only on the editor mount leaves the `:root`-scoped chrome on Apollon's own
 * defaults (a near-black blue-grey that clashes with Artemis's dark surfaces). Artemis
 * therefore stamps this map onto `<html>` (see {@link applyArtemisApollonThemeToDocument});
 * the editor, which carries no `data-theme` of its own, inherits every `--apollon-*` from
 * there. The same map is also passed as the `theme` option so the mount is themed directly.
 *
 * Specialised tokens (assessment score tints, the colour-picker swatch palette, the
 * collaboration cursor hues, the canvas grid) are intentionally left to Apollon's own
 * theme-aware defaults: they re-resolve per `data-theme` already and have no clean,
 * dark-safe Artemis equivalent (the PrimeNG surface *scale* does not invert per scheme).
 *
 * Light/dark itself is driven by the `data-theme` attribute that `ThemeService` mirrors
 * onto `<html>`; Apollon reads it from any ancestor.
 *
 * Pass the result as the `theme` option to `new ApollonEditor(el, { theme: artemisApollonTheme() })`
 * and call {@link applyArtemisApollonThemeToDocument} once before creating the editor.
 */
export function artemisApollonTheme(): Partial<Record<`--apollon-${string}`, string>> {
    return {
        // Brand accent — selection, links, highlights, and the chrome accent.
        '--apollon-primary': 'var(--p-primary-color)',
        // Ink on the canvas; also drives the derived chrome text/border ramp.
        '--apollon-primary-contrast': 'var(--p-text-color)',
        '--apollon-secondary': 'var(--p-text-muted-color)',
        // Canvas + raised popover/menu surfaces follow Artemis's content surfaces.
        '--apollon-background': 'var(--p-content-background)',
        '--apollon-surface': 'var(--p-content-background)',
        '--apollon-surface-hover': 'var(--p-content-hover-background)',
        // Borders / dividers.
        '--apollon-border': 'var(--p-content-border-color)',
        '--apollon-border-subtle': 'var(--p-content-border-color)',
    };
}

/**
 * Stamps {@link artemisApollonTheme} onto the document root (`<html>`) as inline custom
 * properties, so Apollon's `:root`-scoped chrome derivation resolves against Artemis's
 * PrimeNG tokens rather than Apollon's built-in defaults.
 *
 * Idempotent and cheap: the stamped values are static `var(--p-*)` references, so a single
 * call themes every current and future editor on the page and needs no re-application on
 * theme toggle. Safe to call on each editor init.
 */
export function applyArtemisApollonThemeToDocument(doc: Document = document): void {
    const root = doc.documentElement;
    const theme = artemisApollonTheme();
    for (const [token, value] of Object.entries(theme)) {
        root.style.setProperty(token, value!);
    }
}
