/**
 * Forbid raw Tailwind color palette utility classes (e.g. `text-green-500`, `bg-red-100`,
 * `border-t-sky-400`, `bg-green-500/50`, `text-[#ff0000]`) in client templates. Raw palette / arbitrary
 * color classes are not bound to the Artemis brand tokens, so they silently break theming and dark mode.
 * Use the semantic tokens instead:
 *   - PrimeNG / tailwindcss-primeui: text-primary, bg-primary, text-primary-contrast,
 *     text-surface-*, bg-surface-*, text-muted-color
 *   - Artemis state tokens: text-success, bg-danger, border-warning, text-info
 *
 * Scope: static `class="..."`, `[class.<palette>]`, and the bound expression source of `[class]="…"` /
 * `[ngClass]="…"` (incl. variant prefixes, `/opacity` and `!important` modifiers, and arbitrary
 * `-[#|rgb|hsl …]` color values). Brand-bound `-(--token)` arbitrary values are intentionally allowed.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */
const TAILWIND_COLOR_NAMES = [
    'slate',
    'gray',
    'grey',
    'zinc',
    'neutral',
    'stone',
    'red',
    'orange',
    'amber',
    'yellow',
    'lime',
    'green',
    'emerald',
    'teal',
    'cyan',
    'sky',
    'blue',
    'indigo',
    'violet',
    'purple',
    'fuchsia',
    'pink',
    'rose',
];
// Includes directional borders (border-x/y/s/e/t/r/b/l), divide-x/y, and ring-offset, whose color step
// sits after an infix (e.g. `border-t-red-500`, `ring-offset-blue-200`).
const COLOR_PREFIXES = [
    'text',
    'bg',
    'fill',
    'stroke',
    'from',
    'to',
    'via',
    'outline',
    'decoration',
    'accent',
    'caret',
    'placeholder',
    'shadow',
    'border',
    'border-x',
    'border-y',
    'border-s',
    'border-e',
    'border-t',
    'border-r',
    'border-b',
    'border-l',
    'divide',
    'divide-x',
    'divide-y',
    'ring',
    'ring-offset',
];

// A color prefix at a class boundary followed by EITHER a palette-color step (with optional `/opacity`
// and `!important` modifiers) OR an arbitrary color value (`-[#…]`, `-[rgb…]`, `-[hsl…]`). The `g` flag
// lets a single class attribute report every offending class.
const RAW_PALETTE = new RegExp(`(?<![\\w-])(?:${COLOR_PREFIXES.join('|')})-(?:(?:${TAILWIND_COLOR_NAMES.join('|')})-\\d{2,3}(?:/\\d+)?!?|\\[(?:#|rgb|hsl)[^\\]]*\\]?)`, 'g');

function scanForPalette(text, node, context) {
    if (typeof text !== 'string') {
        return;
    }
    RAW_PALETTE.lastIndex = 0;
    let match;
    while ((match = RAW_PALETTE.exec(text)) !== null) {
        context.report({ node, messageId: 'rawPalette', data: { cls: match[0] } });
    }
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid raw Tailwind color palette / arbitrary-color utility classes in client templates; use semantic brand tokens instead.',
        },
        messages: {
            rawPalette:
                "Raw Tailwind color class '{{cls}}' is not allowed — it is not brand-bound and breaks theming/dark mode. Use a semantic token (text-primary / text-surface-* / text-muted-color / text-success|danger|warning|info). See client-development.mdx (### Styling).",
        },
        schema: [],
    },
    create(context) {
        return {
            // Static class="...".
            TextAttribute(node) {
                if (node.name === 'class') {
                    scanForPalette(node.value, node, context);
                }
            },
            // [class.<palette>]="x": the palette token is the attribute name itself.
            // [class]="…" / [ngClass]="…": the palette lives in the bound expression source.
            BoundAttribute(node) {
                if (node.name === 'class' || node.name === 'ngClass') {
                    scanForPalette(node.value?.source, node, context);
                } else if (typeof node.name === 'string') {
                    scanForPalette(node.name, node, context);
                }
            },
        };
    },
};
