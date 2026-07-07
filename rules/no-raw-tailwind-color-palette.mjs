/**
 * Forbid color classes that are NOT brand-bound semantic tokens in client templates:
 *   1. Raw Tailwind palette / arbitrary colors — `text-green-500`, `bg-red-100`, `border-t-sky-400`,
 *      `bg-green-500/50`, `text-[#ff0000]`. Not bound to the brand, so they break theming and dark mode.
 *   2. PrimeNG PRIMITIVE palette tokens used to express meaning — `text-(--p-red-500)`, `bg-(--p-green-300)`,
 *      and `var(--p-red-500)` inside `[style.*]` / `[color]` bindings. These ARE brand-bound (AuraArtemis maps
 *      `--p-red-500 -> var(--danger)`), but naming the PRIMITIVE tier ("red-500") to express a SEMANTIC role
 *      ("danger") is the documented design-token anti-pattern: it drifts and reads inconsistently.
 *   3. The older ARBITRARY brand-state form — `text-(--danger)`, `bg-(--success)` — superseded by the named
 *      `state-*` utilities below. (Only the four brand-state vars are flagged; component-local arbitrary vars
 *      like `text-(--artemis-alert-danger-color)`, and `var(--danger)` inside `[style.*]`, remain allowed.)
 *
 * Use the SEMANTIC tokens instead:
 *   - PrimeNG / tailwindcss-primeui (always-available, collision-safe): text-primary, bg-primary,
 *     text-primary-contrast, text-surface-*, bg-surface-*, text-muted-color, text-color.
 *   - Artemis state tokens via the NAMED `state-*` utilities (collision-free + dark-mode correct, defined by the
 *     `@theme inline` block in tailwind.css): text-state-danger, bg-state-success, border-state-warning,
 *     text-state-info; or `[style.color]="'var(--danger)'"` when the color is DYNAMIC; or a PrimeNG `severity`
 *     component (p-message / p-tag / p-button). info is CYAN: use state-info, never blue.
 *
 * Scope: static `class="..."`, `[class.<palette>]`, the bound source of `[class]`/`[ngClass]`, and the bound
 * source of any binding (to catch `var(--p-*-NNN)` in `[style.*]`/`[color]`).
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

// A color prefix followed by an arbitrary value referencing a PrimeNG PRIMITIVE palette token, in EITHER
// arbitrary form: the v4 shorthand `text-(--p-red-500)` OR the bracketed `text-[var(--p-green-300)]`.
// Semantic tokens (--p-primary-color, --p-text-muted-color, --danger, --surface-100) have no
// `<colorname>-<number>` shape and are therefore NOT matched.
const PRIMITIVE_UTILITY = new RegExp(
    `(?<![\\w-])(?:${COLOR_PREFIXES.join('|')})-(?:\\(\\s*--p-(?:${TAILWIND_COLOR_NAMES.join('|')})-\\d{2,3}\\s*\\)|\\[\\s*var\\(\\s*--p-(?:${TAILWIND_COLOR_NAMES.join('|')})-\\d{2,3}\\s*\\)\\s*\\])`,
    'g',
);

// `var(--p-<colorname>-<number>)` anywhere in a bound expression source — catches the inline-style escape
// hatch, e.g. `[style.color]="'var(--p-red-500)'"`, `[color]="'var(--p-green-500)'"`.
const PRIMITIVE_VAR = new RegExp(`var\\(\\s*--p-(?:${TAILWIND_COLOR_NAMES.join('|')})-\\d{2,3}\\s*\\)`, 'g');

// The older arbitrary brand-state form, in EITHER form: `text-(--danger)` OR the bracketed
// `text-[var(--success)]`. Superseded by the named `state-*` utilities (collision-free, dark-mode correct).
// Only the four brand-state vars are matched, so component-local arbitrary vars (`--artemis-alert-*`) and
// `var(--danger)` inside `[style.*]` are unaffected.
const STATE_ARBITRARY = new RegExp(
    `(?<![\\w-])(?:${COLOR_PREFIXES.join('|')})-(?:\\(\\s*--(?:danger|success|warning|info)\\s*\\)|\\[\\s*var\\(\\s*--(?:danger|success|warning|info)\\s*\\)\\s*\\])`,
    'g',
);

function scanWith(regex, messageId, text, node, context) {
    if (typeof text !== 'string') {
        return;
    }
    regex.lastIndex = 0;
    let match;
    while ((match = regex.exec(text)) !== null) {
        context.report({ node, messageId, data: { cls: match[0] } });
    }
}

function scanClasses(text, node, context) {
    scanWith(RAW_PALETTE, 'rawPalette', text, node, context);
    scanWith(PRIMITIVE_UTILITY, 'primitivePalette', text, node, context);
    scanWith(STATE_ARBITRARY, 'arbitraryStateToken', text, node, context);
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid raw Tailwind palette / arbitrary-color classes and PrimeNG primitive palette tokens used for meaning; use semantic brand tokens instead.',
        },
        messages: {
            rawPalette:
                "Raw Tailwind color class '{{cls}}' is not allowed — it is not brand-bound and breaks theming/dark mode. Use a semantic token (text-primary / text-surface-* / text-muted-color / text-state-danger|success|warning|info). See client-development.mdx (### Styling).",
            primitivePalette:
                "PrimeNG primitive palette token '{{cls}}' is used to express meaning — primitives encode VALUE (red-500), not INTENT. Use the SEMANTIC token: text-state-danger/success/warning/info (info is cyan), or a PrimeNG severity component. See client-development.mdx (### Styling).",
            arbitraryStateToken:
                "Arbitrary brand-state utility '{{cls}}' is superseded — use the named token instead (e.g. text-(--danger) -> text-state-danger, bg-(--success) -> bg-state-success). Named state tokens are collision-free and dark-mode correct. For DYNAMIC color, bind [style.color]=\"'var(--danger)'\". See client-development.mdx (### Styling).",
        },
        schema: [],
    },
    create(context) {
        // `class` plus PrimeNG class inputs (`styleClass`, component-specific `*StyleClass`), which render their
        // classes onto the host — migrated templates pass Tailwind colour utilities through them, so raw palette /
        // primitives there must be caught too.
        const isClassListAttr = (name) => name === 'class' || name === 'styleClass' || name.endsWith('StyleClass');
        return {
            // Static class="..." / styleClass="...".
            TextAttribute(node) {
                if (isClassListAttr(node.name)) {
                    scanClasses(node.value, node, context);
                }
            },
            // [class]/[ngClass]/[styleClass]="…": the palette lives in the bound expression source.
            // [class.<palette>]="x": the palette token is the attribute name itself.
            // Any other binding ([style.color], [color], …): catch `var(--p-<palette>-NNN)` in the expression source.
            BoundAttribute(node) {
                if (isClassListAttr(node.name) || node.name === 'ngClass') {
                    scanClasses(node.value?.source, node, context);
                } else if (node.keySpan?.details?.startsWith('class.')) {
                    // [class.<palette>]="x" — the token is the attribute name. Gate on the `class.` key so a
                    // component INPUT that happens to share the name is not scanned as a class.
                    scanClasses(node.name, node, context);
                } else {
                    scanWith(PRIMITIVE_VAR, 'primitivePalette', node.value?.source, node, context);
                }
            },
        };
    },
};
