/**
 * Maps Apollon's public `--apollon-*` theming contract onto Artemis's live PrimeNG
 * design tokens, so the embedded modeling editor adopts Artemis's primary colour,
 * surfaces, text and borders — in light **and** dark — and re-colours automatically
 * when the user toggles the theme.
 *
 * The values are `var(--p-*)` references resolved through the CSS cascade, so they are
 * written onto the editor's mount node once (as the highest-specificity inline styles)
 * and stay live with zero re-application: when Artemis flips the theme, the PrimeNG
 * tokens re-resolve and the editor re-colours for free.
 *
 * Apollon's floating chrome (element palette, zoom/undo controls, minimap, rails) is
 * `color-mix()`-derived from `--apollon-background` + `--apollon-primary-contrast`, so
 * theming those two (plus `--apollon-primary`) themes the whole editor cohesively —
 * which is why this map is deliberately small.
 *
 * Specialised tokens (assessment score tints, the colour-picker swatch palette, the
 * collaboration cursor hues, the canvas grid) are intentionally left to Apollon's own
 * theme-aware defaults: they re-resolve per `data-theme` already and have no clean,
 * dark-safe Artemis equivalent.
 *
 * Light/dark itself is driven by the `data-theme` attribute that `ThemeService` mirrors
 * onto `<html>`; Apollon reads it from any ancestor.
 *
 * Pass the result as the `theme` option to `new ApollonEditor(el, { theme: artemisApollonTheme() })`.
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
