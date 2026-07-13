/**
 * WCAG AA contrast regression coverage for the tum-aet-ui kit's state palette (buttons + tags).
 *
 * jsdom cannot resolve CSS custom properties or `color-mix()`, so this spec pins the SAME resolved hex
 * values declared in `content/scss/themes/_default-variables.scss` (light) and `_dark-variables.scss`
 * (dark) and recomputes the contrast the browser would render. If anyone weakens a `-solid` / `-strong`
 * tone (or the base state color used behind a tag tint), the matching pairing drops below 4.5:1 and this
 * test fails — keeping the accessible tokens honest. Keep the constants below in sync with the SCSS.
 *
 * Pairings asserted, per theme and severity:
 *  - solid button:      white label on the `-solid` fill
 *  - outlined/text btn: `-strong` label on the page surface
 *  - tag:               `-strong` label on the `bg-state-X/15` tint (15% base over the surface)
 */
import { describe, expect, it } from 'vitest';

type Rgb = [number, number, number];
type Severity = 'primary' | 'danger' | 'success' | 'info' | 'warning';
type Palette = Record<Severity, string>;

const AA_NORMAL = 4.5;

function toRgb(hexColor: string): Rgb {
    const hex = hexColor.replace('#', '');
    const full = hex.length === 3 ? [...hex].map((c) => c + c).join('') : hex;
    return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16) / 255) as Rgb;
}

/** color-mix(in srgb, top pTop%, bottom) — linear blend of gamma-encoded sRGB channels. */
function mix(top: Rgb, bottom: Rgb, pTop: number): Rgb {
    return top.map((c, i) => c * pTop + bottom[i] * (1 - pTop)) as Rgb;
}

function relativeLuminance([r, g, b]: Rgb): number {
    const channel = (c: number) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrastRatio(foreground: string, background: Rgb): number {
    const [lighter, darker] = [relativeLuminance(toRgb(foreground)), relativeLuminance(background)].sort((a, b) => b - a);
    return (lighter + 0.05) / (darker + 0.05);
}

const WHITE = '#ffffff';

interface ThemePalette {
    surface: string; // lightest surface a foreground sits on (worst case for contrast)
    base: Palette; // --danger / --success / … (behind a tag tint)
    solid: Palette; // --*-dark  (solid button fill, white text)
    strong: Palette; // --*-strong (outlined/text/tag foreground)
}

const THEMES: Record<'light' | 'dark', ThemePalette> = {
    light: {
        surface: WHITE,
        base: { primary: '#3e8acc', danger: '#dc3545', success: '#28a745', info: '#17a2b8', warning: '#ffc107' },
        solid: { primary: '#1f5c9a', danger: '#b02a37', success: '#157347', info: '#0e7490', warning: '#856404' },
        strong: { primary: '#1f5c9a', danger: '#b02a37', success: '#157347', info: '#0e7490', warning: '#856404' },
    },
    dark: {
        surface: '#262b31', // surface-800: the lightest dark card surface (real panels sit darker)
        base: { primary: '#3e8acc', danger: '#e74c3c', success: '#00bc8c', info: '#17a2b8', warning: '#f39c12' },
        solid: { primary: '#2d6cae', danger: '#a52a1e', success: '#08795b', info: '#106577', warning: '#8a5a06' },
        strong: { primary: '#7cb0e0', danger: '#ff8a80', success: '#2dd4aa', info: '#4dc7db', warning: '#ffca4d' },
    },
};

const SEVERITIES: Severity[] = ['primary', 'danger', 'success', 'info', 'warning'];

describe('tum-ui state palette WCAG AA contrast', () => {
    for (const theme of ['light', 'dark'] as const) {
        const p = THEMES[theme];
        const surfaceRgb = toRgb(p.surface);

        describe(`${theme} theme`, () => {
            for (const severity of SEVERITIES) {
                it(`solid ${severity}: white on the -solid fill ≥ 4.5:1`, () => {
                    expect(contrastRatio(WHITE, toRgb(p.solid[severity]))).toBeGreaterThanOrEqual(AA_NORMAL);
                });

                it(`outlined/text ${severity}: -strong on the surface ≥ 4.5:1`, () => {
                    expect(contrastRatio(p.strong[severity], surfaceRgb)).toBeGreaterThanOrEqual(AA_NORMAL);
                });

                it(`tag ${severity}: -strong on the 15% tint ≥ 4.5:1`, () => {
                    const tint = mix(toRgb(p.base[severity]), surfaceRgb, 0.15);
                    expect(contrastRatio(p.strong[severity], tint)).toBeGreaterThanOrEqual(AA_NORMAL);
                });
            }
        });
    }
});
