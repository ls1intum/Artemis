import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { readThemeTokens } from 'test/helpers/theme-tokens';

/**
 * A solid success button (for example "Example submissions") must show a white label that stays readable, and this must not recolour anything else.
 *
 * The solid button of the UI kit reads `--tumaet-ui-button-success-background` and `--tumaet-ui-button-success-contrast`. The app sets both in `tailwind.css`
 * for the light theme. The success status colour (`--tumaet-ui-state-success*`) is shared with messages, tags and badges, for example the accepted complaint
 * badge that combines the green fill with the contrast colour, so it must keep its own readable pair. The values are not re-typed here: the blocks are read
 * from the stylesheet and `--success` comes from the tokens of the light theme.
 */
const TAILWIND_CSS = 'src/main/webapp/tailwind.css';

/** WCAG 2.x AA contrast for normal-sized text. */
const AA_TEXT = 4.5;

type Rgb = [number, number, number];

function parseHex(value: string): Rgb {
    const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(value.trim());
    if (!hex) {
        throw new Error(`Unsupported colour "${value}": extend parseHex if the theme now uses another colour syntax.`);
    }
    const digits = hex[1].length === 3 ? [...hex[1]].map((digit) => digit + digit) : (hex[1].match(/../g) ?? []);
    return digits.map((digit) => parseInt(digit, 16)) as Rgb;
}

/** Resolves `var(--name)` and `color-mix(in srgb, <colour> <percent>%, black)` to an RGB triple. */
function resolve(value: string, tokens: Map<string, string>): Rgb {
    const variable = /^var\(--([\w-]+)\)$/.exec(value.trim());
    if (variable) {
        const tokenValue = tokens.get(variable[1]);
        if (tokenValue === undefined) {
            throw new Error(`The default theme does not define --${variable[1]}`);
        }
        return resolve(tokenValue, tokens);
    }
    const mix = /^color-mix\(in srgb,\s*(.+?)\s+(\d+(?:\.\d+)?)%,\s*black\)$/.exec(value.trim());
    if (mix) {
        const share = parseFloat(mix[2]) / 100;
        return resolve(mix[1], tokens).map((channel) => channel * share) as Rgb;
    }
    return parseHex(value);
}

function relativeLuminance([red, green, blue]: Rgb): number {
    const linear = (channel: number) => {
        const unit = channel / 255;
        return unit <= 0.04045 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
}

function contrastRatio(first: Rgb, second: Rgb): number {
    const [lighter, darker] = [relativeLuminance(first), relativeLuminance(second)].sort((a, b) => b - a);
    return (lighter + 0.05) / (darker + 0.05);
}

function stylesheet(): string {
    return readFileSync(TAILWIND_CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
}

function declarationsOf(block: RegExpExecArray | RegExpMatchArray | undefined, description: string): Map<string, string> {
    if (!block) {
        throw new Error(`${TAILWIND_CSS} has no ${description}`);
    }
    return new Map([...block[1].matchAll(/(--[\w-]+):\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim()]));
}

/** The declarations of the `:root` block that maps the UI kit roles, which is the light theme. */
function lightThemeDeclarations(): Map<string, string> {
    const block = [...stylesheet().matchAll(/^:root\s*\{([\s\S]*?)\n\}/gm)].find(([, body]) => body.includes('--tumaet-ui-state-success:'));
    return declarationsOf(block, ':root block that maps --tumaet-ui-state-success');
}

/** The declarations of the dark theme block. */
function darkThemeDeclarations(): Map<string, string> {
    return declarationsOf(/^\[data-theme='dark'\]\s*\{([\s\S]*?)\n\}/m.exec(stylesheet()) ?? undefined, "[data-theme='dark'] block");
}

describe('Success button contrast', () => {
    const light = lightThemeDeclarations();
    const dark = darkThemeDeclarations();
    const tokens = readThemeTokens('light');
    const buttonBackground = resolve(light.get('--tumaet-ui-button-success-background')!, tokens);
    const buttonLabel = resolve(light.get('--tumaet-ui-button-success-contrast')!, tokens);

    it('should show a white label on a solid success button', () => {
        expect(buttonLabel).toEqual([255, 255, 255]);
    });

    it('should keep the white label readable on the button background', () => {
        expect(contrastRatio(buttonLabel, buttonBackground)).toBeGreaterThanOrEqual(AA_TEXT);
    });

    it('should leave the success status colour alone, because badges combine its fill with its contrast colour', () => {
        expect(light.get('--tumaet-ui-state-success')).toBe('var(--success)');
        const statusFill = resolve(light.get('--tumaet-ui-state-success')!, tokens);
        const statusLabel = resolve(light.get('--tumaet-ui-state-success-contrast')!, tokens);

        expect(contrastRatio(statusLabel, statusFill)).toBeGreaterThanOrEqual(AA_TEXT);
    });

    it('should not reach the dark theme, which keeps the success button that follows the status colour', () => {
        expect(dark.get('--tumaet-ui-button-success-background')).toBe('var(--tumaet-ui-state-success)');
        expect(dark.get('--tumaet-ui-button-success-contrast')).toBe('var(--tumaet-ui-state-success-contrast)');
    });
});
