import { describe, expect, it } from 'vitest';
import { Logger, compileString } from 'sass';

/**
 * Contrast guard for the highlight that a hovered message gets in the dark theme, and a parity guard for the communication
 * tokens of both themes. The light theme is only checked for a visible highlight and readable message text: its link, grey
 * and inline code colours are below AA on the highlight already and are not part of this guard.
 *
 * The tokens are not re-typed here. Both theme variable files are compiled with the same `generate-css-vars` mixin that
 * produces `theme-default.css` and `theme-dark.css`, so a change to a value in `_dark-variables.scss` (or to a `lighten()`
 * step it is derived from) is what these assertions see. The colours they check are the ones a message renders: the primary
 * text, the secondary text of the role label, links, inline code, block quotes and the muted grey of the "edited" label.
 */
const THEME_DIRECTORY = 'src/main/webapp/content/scss/themes';

/** WCAG 2.x AA contrast for normal-sized text. */
const AA_TEXT = 4.5;

/**
 * Largest contrast ratio between the dark highlight and the message background. The issue asks for a smaller contrast: the
 * highlight is a hint on one surface, not a second surface, and every step above this one takes the role label, links and
 * inline code of the dark theme below AA unless their colours are lightened app-wide (the first dark highlight was 1.15).
 */
const MAX_HIGHLIGHT_STEP = 1.1;

/** Smallest contrast ratio at which the highlight can still be told apart from the message background. */
const MIN_HIGHLIGHT_STEP = 1.03;

/** Tokens of text that is rendered inside a message, with where it shows up. */
const MESSAGE_TEXT_TOKENS: Record<string, string> = {
    'body-color': 'message text and author name',
    'body-secondary-color': 'role label below the author name',
    'link-color': 'links in a message',
    'markdown-preview-blockquote': 'block quote',
    'communication-gray': 'edited label and other muted message text',
};

type Rgba = [number, number, number, number];

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

function parseChannel(channel: string, scale: number): number {
    return channel.endsWith('%') ? (parseFloat(channel) / 100) * scale : parseFloat(channel);
}

function parseColor(value: string): Rgba {
    const text = value.trim().toLowerCase();
    if (text === 'white') {
        return [255, 255, 255, 1];
    }
    if (text === 'black') {
        return [0, 0, 0, 1];
    }
    const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/.exec(text);
    if (hex) {
        const digits = hex[1].length === 3 ? [...hex[1]].map((digit) => digit + digit) : (hex[1].match(/../g) ?? []);
        const [red, green, blue] = digits.map((digit) => parseInt(digit, 16));
        return [red, green, blue, 1];
    }
    const functional = /^rgba?\((.+)\)$/.exec(text);
    if (functional) {
        const [red, green, blue, alpha] = functional[1].split(/[\s,/]+/).filter(Boolean);
        return [parseChannel(red, 255), parseChannel(green, 255), parseChannel(blue, 255), alpha === undefined ? 1 : parseChannel(alpha, 1)];
    }
    throw new Error(`Unsupported colour "${value}": extend parseColor if a theme variable now uses another colour syntax.`);
}

function tokenColor(tokens: Map<string, string>, name: string): Rgba {
    const value = tokens.get(name);
    if (value === undefined) {
        throw new Error(`The theme does not define --${name}`);
    }
    return parseColor(value);
}

/** Paints `foreground` (which may be translucent) over the opaque `background`. */
function over(foreground: Rgba, background: Rgba): Rgba {
    const alpha = foreground[3];
    return [0, 1, 2].map((channel) => foreground[channel] * alpha + background[channel] * (1 - alpha)).concat(1) as Rgba;
}

function relativeLuminance([red, green, blue]: Rgba): number {
    const linear = (channel: number) => {
        const unit = channel / 255;
        return unit <= 0.04045 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
}

function contrastRatio(first: Rgba, second: Rgba): number {
    const [lighter, darker] = [relativeLuminance(first), relativeLuminance(second)].sort((a, b) => b - a);
    return (lighter + 0.05) / (darker + 0.05);
}

describe('Communication message highlight contrast', () => {
    describe('dark theme', () => {
        const tokens = compileThemeTokens('dark-variables');
        const messageBackground = tokenColor(tokens, 'module-bg');
        const highlight = over(tokenColor(tokens, 'communication-selection-option-hover-background'), messageBackground);

        it('should set the highlight apart from the message background by a subtle step', () => {
            const step = contrastRatio(highlight, messageBackground);

            expect(step).toBeGreaterThanOrEqual(MIN_HIGHLIGHT_STEP);
            expect(step).toBeLessThanOrEqual(MAX_HIGHLIGHT_STEP);
        });

        it.each(Object.entries(MESSAGE_TEXT_TOKENS))('should keep --%s (%s) readable on the message background and on the highlight', (name) => {
            const text = tokenColor(tokens, name);

            expect(contrastRatio(text, messageBackground), `--${name} on the message background`).toBeGreaterThanOrEqual(AA_TEXT);
            expect(contrastRatio(text, highlight), `--${name} on the highlight`).toBeGreaterThanOrEqual(AA_TEXT);
        });

        it('should keep inline code readable on the message background and on the highlight', () => {
            // Bootstrap colours <code> with $code-color, which defaults to $pink.
            const code = tokens.has('code-color') ? tokenColor(tokens, 'code-color') : tokenColor(tokens, 'pink');

            expect(contrastRatio(code, messageBackground), 'inline code on the message background').toBeGreaterThanOrEqual(AA_TEXT);
            expect(contrastRatio(code, highlight), 'inline code on the highlight').toBeGreaterThanOrEqual(AA_TEXT);
        });
    });

    describe('light theme', () => {
        const tokens = compileThemeTokens('default-variables');
        const messageBackground = tokenColor(tokens, 'module-bg');
        const highlight = over(tokenColor(tokens, 'communication-selection-option-hover-background'), messageBackground);

        it('should still tell the highlight apart from the message background', () => {
            expect(contrastRatio(highlight, messageBackground)).toBeGreaterThanOrEqual(MIN_HIGHLIGHT_STEP);
        });

        it('should keep the message text readable on the highlight', () => {
            expect(contrastRatio(tokenColor(tokens, 'body-color'), highlight)).toBeGreaterThanOrEqual(AA_TEXT);
        });
    });
});

describe('Communication theme tokens', () => {
    it('should define every communication token in the dark theme too, so that none falls back to its light value', () => {
        const light = compileThemeTokens('default-variables');
        const dark = compileThemeTokens('dark-variables');
        const communicationTokens = [...light.keys()].filter((name) => name.startsWith('communication-') || name.startsWith('reaction-button-'));

        expect(communicationTokens).toContain('communication-selection-option-hover-background');
        expect(communicationTokens.filter((name) => !dark.has(name))).toEqual([]);
    });
});
