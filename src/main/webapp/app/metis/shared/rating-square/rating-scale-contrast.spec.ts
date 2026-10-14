import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { Logger, compileString } from 'sass';

/**
 * Contrast guard for the digits of the rating squares. Each of the five steps of the rating scale has a theme property for its background and one for
 * the colour of text on it. Both theme variable files are compiled with the same `generate-css-vars` mixin that produces the theme style sheets, so the
 * values checked here are the ones the squares render. The digits are 12 to 14 pixels, so they need the AA contrast of normal text.
 */
const THEME_DIRECTORY = 'src/main/webapp/content/scss/themes';

/** WCAG 2.x AA contrast for normal-sized text. */
const AA_TEXT = 4.5;

const STEPS = ['lowest', 'low', 'neutral', 'high', 'highest'];

type Rgb = [number, number, number];

function compileThemeTokens(variablesModule: 'default-variables' | 'dark-variables'): Map<string, string> {
    const { css } = compileString(`@use '${THEME_DIRECTORY}/generate-css-vars';\nhtml { @include generate-css-vars.generate('${variablesModule}'); }`, {
        loadPaths: [process.cwd()],
        logger: Logger.silent,
    });
    const tokens = new Map<string, string>();
    for (const [, name, value] of css.matchAll(/--([\w-]+):\s*([^;]+);/g)) {
        tokens.set(name, value.trim());
    }
    return tokens;
}

function parseColor(value: string): Rgb {
    const text = value.trim().toLowerCase();
    if (text === 'white') {
        return [255, 255, 255];
    }
    if (text === 'black') {
        return [0, 0, 0];
    }
    const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/.exec(text);
    if (!hex) {
        throw new Error(`Unsupported colour "${value}": the rating scale is defined in hexadecimal colours`);
    }
    const digits = hex[1].length === 3 ? [...hex[1]].map((digit) => digit + digit) : (hex[1].match(/../g) ?? []);
    return digits.map((digit) => parseInt(digit, 16)) as Rgb;
}

function relativeLuminance(color: Rgb): number {
    const linear = (channel: number) => {
        const unit = channel / 255;
        return unit <= 0.04045 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
    };
    const [red, green, blue] = color.map(linear);
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrastRatio(first: Rgb, second: Rgb): number {
    const [lighter, darker] = [relativeLuminance(first), relativeLuminance(second)].sort((a, b) => b - a);
    return (lighter + 0.05) / (darker + 0.05);
}

describe('rating scale contrast', () => {
    for (const theme of ['default-variables', 'dark-variables'] as const) {
        const tokens = compileThemeTokens(theme);

        it.each(STEPS)(`should keep the digits on the %s step readable in ${theme}`, (step) => {
            const background = tokens.get(`rating-scale-${step}`);
            const foreground = tokens.get(`rating-scale-${step}-contrast`);

            expect(background, `--rating-scale-${step}`).toBeDefined();
            expect(foreground, `--rating-scale-${step}-contrast`).toBeDefined();
            expect(contrastRatio(parseColor(foreground!), parseColor(background!))).toBeGreaterThanOrEqual(AA_TEXT);
        });
    }

    it('should give the digits of every step of the square the colour of that step', () => {
        const styles = readFileSync('src/main/webapp/app/metis/shared/rating-square/rating-square.component.scss', 'utf8');

        for (const step of STEPS) {
            expect(styles).toMatch(new RegExp(`&--${step} \\{\\s*background: var\\(--rating-scale-${step}\\);\\s*color: var\\(--rating-scale-${step}-contrast\\);`));
        }
    });
});
