import { Injectable, Signal, computed, effect, inject } from '@angular/core';
import { Chart } from 'chart.js';
import { ThemeService } from 'app/core/theme/shared/theme.service';

const CSS_VARIABLE_PATTERN = /^var\((--[\w-]+)\)$/;

export const FALLBACK_CHART_COLOR = '#999999';
const FALLBACK_TEXT_COLOR = '#666666';
const FALLBACK_GRID_COLOR = 'rgba(127, 127, 127, 0.2)';

/**
 * Resolves a CSS custom property reference of the form 'var(--name)' to its concrete value.
 * Plain color strings (hex, rgb(a), named colors) are passed through unchanged.
 * Charts render to a canvas, which cannot consume 'var(...)' strings directly.
 */
export function resolveCssColor(color: string, fallback = FALLBACK_CHART_COLOR): string {
    const match = CSS_VARIABLE_PATTERN.exec(color.trim());
    if (!match) {
        return color;
    }
    const resolved = getComputedStyle(document.documentElement).getPropertyValue(match[1]).trim();
    return resolved || fallback;
}

/**
 * Provides theme-aware resolution of chart colors.
 *
 * Artemis chart colors are defined as CSS custom properties (see {@code GraphColors}) so that the
 * light and dark themes can supply different values. Since chart.js draws on a canvas, those
 * variables must be resolved to concrete colors and re-resolved whenever the active theme's
 * stylesheet changes. Resolution is keyed off {@code ThemeService.appliedThemeRevision}, which only
 * advances once the new theme's CSS variables are actually in effect.
 *
 * The service also keeps the global chart.js text and grid colors ({@code Chart.defaults.color} /
 * {@code Chart.defaults.borderColor}) in sync with the active theme. chart.js consults these
 * defaults at draw time for axis ticks, scale titles, legend labels, and data labels, so individual
 * chart options never need to configure text colors.
 */
@Injectable({ providedIn: 'root' })
export class ChartColorService {
    private themeService = inject(ThemeService);

    constructor() {
        effect(() => {
            this.themeService.appliedThemeRevision();
            Chart.defaults.color = resolveCssColor('var(--chart-text-color)', FALLBACK_TEXT_COLOR);
            Chart.defaults.borderColor = resolveCssColor('var(--chart-grid-color)', FALLBACK_GRID_COLOR);
        });
    }

    /**
     * Resolves a single color, see {@link resolveCssColor}. Not reactive — use {@link resolvedColors}
     * (or call this inside a computed that already depends on it) for theme-reactive colors.
     */
    resolve(color: string, fallback = FALLBACK_CHART_COLOR): string {
        return resolveCssColor(color, fallback);
    }

    /**
     * Returns a signal of concrete colors that re-resolves whenever the applied theme changes.
     * Chart data computed from this signal therefore gets a new object reference on theme switch,
     * which re-renders the chart with the new palette.
     *
     * @param colors function (may read other signals) producing the raw colors, e.g. GraphColors values
     */
    resolvedColors(colors: () => string[]): Signal<string[]> {
        return computed(() => {
            this.themeService.appliedThemeRevision();
            return colors().map((color) => this.resolve(color));
        });
    }
}
