/**
 * Maps Apollon's `--apollon-*` theming contract onto Artemis's PrimeNG design tokens so the
 * embedded editor tracks Artemis's colours in light and dark. Every value is a `var(--p-*)`
 * reference resolved through the cascade, so the map is stamped once and needs no
 * re-application on theme toggle: the tokens re-resolve and the editor re-colours automatically.
 *
 * Apply it via {@link applyArtemisApollonThemeToDocument}, not per editor mount: Apollon derives
 * its floating chrome (palette, controls, minimap) from `--apollon-background` +
 * `--apollon-primary-contrast` via a `color-mix()` ramp declared at `:root`, so the tokens must
 * live on the document root for the chrome to follow. The editor sets no `data-theme` of its own
 * and inherits every `--apollon-*` from there.
 *
 * Specialised tokens (assessment tints, colour-picker swatches, collaboration cursors, the grid)
 * are left to Apollon's theme-aware defaults: they already re-resolve per `data-theme` and have
 * no dark-safe Artemis equivalent (the PrimeNG surface *scale* does not invert per scheme).
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
 * Stamps {@link artemisApollonTheme} onto the document root (`<html>`) as inline custom properties.
 * Idempotent and cheap; the stamped `var(--p-*)` references are static, so one call themes every
 * editor on the page and needs no re-application on theme toggle. Safe to call on each editor init.
 */
export function applyArtemisApollonThemeToDocument(): void {
    const root = document.documentElement;
    for (const [token, value] of Object.entries(artemisApollonTheme())) {
        root.style.setProperty(token, value!);
    }
}
