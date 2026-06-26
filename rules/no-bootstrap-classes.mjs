/**
 * Forbid Bootstrap CSS classes in templates of modules that have been migrated to Tailwind + PrimeNG.
 * This is the regression lock for the incremental Bootstrap → PrimeNG/Tailwind migration: once a module's
 * templates are clean, this rule (scoped to that module in eslint.config.mjs) prevents Bootstrap classes
 * from creeping back in.
 *
 * Only classes that are UNAMBIGUOUSLY Bootstrap-only are banned — i.e. they do not collide with a valid
 * Tailwind v4 utility of the same name. In particular the spacing scale (`mb-3`, `me-1`, `p-3`, `gap-2`)
 * and logical-margin utilities (`ms-*`, `me-*`) are NOT banned: Bootstrap and Tailwind share the same class
 * NAMES (the Tailwind value applies after migration), so they are legitimate Tailwind utilities. Color application is governed separately
 * by `no-raw-tailwind-color-palette` (templates) and the stylelint hex/`--bs-` ban (SCSS).
 *
 * Scope: static `class="..."`, `[class.<bootstrap-class>]`, and the bound source of `[class]` / `[ngClass]`.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */

// Each entry matches a single, whole class token (anchored). Grouped by Bootstrap concept with its
// PrimeNG / Tailwind replacement noted for the reviewer. Exported so the migration burndown/check scripts
// (supporting_scripts/migration/) reuse the exact same curated matcher — a naive grep over-reports massively.
export const BANNED = [
    // Buttons -> p-button / p-buttonGroup
    /^btn(-.+)?$/,
    // Badges -> p-tag
    /^badge$/,
    // Cards -> p-card (or a Tailwind `rounded-lg border p-4` box)
    /^card(-.+)?$/,
    // Alerts / callouts -> p-message. Only Bootstrap's own alert classes — not custom `alert-*` names
    // (e.g. the alert overlay's `alert-wrap` / `alert-inner` / `alert-animation-wrapper`).
    /^alert(-(primary|secondary|success|danger|warning|info|light|dark|dismissible|link|heading))?$/,
    // Form controls -> PrimeNG inputs (pInputText / p-select / p-textarea / p-checkbox / p-radiobutton)
    /^form-(control|control-.+|group|select|label|text|range|floating|check|check-input|check-label|check-reverse|switch)$/,
    /^input-group(-.+)?$/,
    /^(valid|invalid)-feedback$/,
    /^col-form-label(-.+)?$/,
    // Bootstrap 12-col grid -> CSS grid/flex (`grid grid-cols-12` + `col-span-*`, or flex). Does NOT match
    // Tailwind's `col-span-*` / `col-start-*` / `col-auto`.
    /^row$/,
    /^col$/,
    /^col-(1[0-2]|[1-9])$/,
    /^col-(sm|md|lg|xl|xxl)(-(auto|1[0-2]|[1-9]))?$/,
    /^g[xy]?-[0-5]$/, // grid gutters -> gap-*
    // Display -> Tailwind `flex` / `hidden` / `block` / `grid` / `inline-block`
    /^d-(none|inline|inline-block|block|grid|flex|inline-flex|table|table-cell|table-row)$/,
    /^d-(sm|md|lg|xl|xxl)-(none|inline|inline-block|block|grid|flex|inline-flex|table|table-cell|table-row)$/,
    // Flexbox helpers -> Tailwind (`justify-*`, `items-*`, `flex-col`, `self-*`)
    /^justify-content-(start|end|center|between|around|evenly)$/,
    /^justify-content-(sm|md|lg|xl|xxl)-(start|end|center|between|around|evenly)$/,
    /^align-items-(start|end|center|baseline|stretch)$/,
    /^align-self-(start|end|center|baseline|stretch|auto)$/,
    /^align-content-(start|end|center|between|around|stretch)$/,
    /^flex-column(-reverse)?$/,
    /^flex-(grow|shrink)-[01]$/,
    /^flex-fill$/,
    // Bootstrap semantic text colors with no brand-bound Tailwind equivalent -> text-muted-color / tokens
    /^text-muted$/,
    /^text-body(-secondary|-tertiary|-emphasis)?$/,
    // Bootstrap semantic state colors (text/border/bg) -> named state tokens, e.g. text-state-danger,
    // bg-state-success, border-state-warning, text-state-info (the arbitrary `text-(--danger)` form is itself
    // banned by no-raw-tailwind-color-palette). Only danger/success/warning/info are matched: those are
    // Bootstrap-only, whereas `*-primary` / `*-surface-*` are PrimeNG tokens and stay allowed.
    /^(text|border|bg)-(danger|success|warning|info)$/,
    // Table component modifiers -> p-table (Tailwind's display `table` / `table-cell` are NOT matched)
    /^table-(striped|striped-columns|bordered|borderless|hover|active|sm|responsive|responsive-(sm|md|lg|xl|xxl)|group-divider)$/,
    // Bootstrap percentage sizing -> Tailwind `h-full`/`w-full` or an explicit Tailwind size. These collide:
    // Bootstrap `.h-100{height:100%!important}` beats Tailwind's `h-100` (= 25rem), so a stray `h-100` silently
    // renders the Bootstrap value. Only 25/50/75/100 are matched; `h-auto`/`w-auto` are shared (width:auto) and stay.
    /^[hw]-(25|50|75|100)$/,
    // Misc Bootstrap-only widgets / utilities
    /^(close|btn-close)$/,
    /^visually-hidden(-focusable)?$/,
    /^text-truncate$/, // -> Tailwind `truncate`
];

export function isBanned(token) {
    return BANNED.some((re) => re.test(token));
}

function scanClassList(text, node, context) {
    if (typeof text !== 'string') {
        return;
    }
    for (const token of text.split(/\s+/)) {
        if (token && isBanned(token)) {
            context.report({ node, messageId: 'bootstrapClass', data: { cls: token } });
        }
    }
}

// Matches single- or double-quoted string literals (e.g. the keys of `[ngClass]="{ 'btn': x }"` or the
// segments of `[class]="'d-flex ' + x"`).
const STRING_LITERAL = /'([^']*)'|"([^"]*)"/g;

// Matches an UNQUOTED object-literal key, e.g. the `btn` of `[ngClass]="{ btn: active }"`. Prettier rewrites a
// quoted `{ 'btn': x }` to this unquoted form, so without this the simplest Bootstrap key bypasses the rule after
// formatting. A key is an identifier directly after `{` or `,` and before `:`; hyphenated class names (`btn-lg`)
// are not valid unquoted identifiers, so they stay quoted and are covered by STRING_LITERAL.
const UNQUOTED_OBJECT_KEY = /[{,]\s*([A-Za-z_$][\w$]*)\s*:/g;

// The bound source of `[class]` / `[ngClass]` is an Angular expression, not a class list. Class names live in
// string literals (`'btn'`) or as unquoted object keys (`{ btn: x }`); scan both with the per-token isBanned() check.
function scanBindingExpression(source, node, context) {
    if (typeof source !== 'string') {
        return;
    }
    for (const re of [STRING_LITERAL, UNQUOTED_OBJECT_KEY]) {
        re.lastIndex = 0;
        let match;
        while ((match = re.exec(source)) !== null) {
            scanClassList(match[1] ?? match[2], node, context);
        }
    }
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid Bootstrap CSS classes in migrated module templates; use Tailwind utilities + PrimeNG components instead.',
        },
        messages: {
            bootstrapClass:
                "Bootstrap class '{{cls}}' is not allowed in a migrated module — use a Tailwind utility or a PrimeNG component (btn->p-button, badge->p-tag, alert->p-message, card->p-card, form-control->PrimeNG input, row/col->grid|flex, d-flex->flex). See client-development.mdx (### Styling).",
        },
        schema: [],
    },
    create(context) {
        return {
            TextAttribute(node) {
                if (node.name === 'class') {
                    scanClassList(node.value, node, context);
                }
            },
            BoundAttribute(node) {
                if (node.name === 'class' || node.name === 'ngClass') {
                    // `[class]`/`[ngClass]` bind an expression (object map or string concat), not a raw
                    // class list — scan the string literals inside it.
                    scanBindingExpression(node.value?.source, node, context);
                } else if (node.keySpan?.details?.startsWith('class.')) {
                    // [class.btn]="..." -> the class token is the attribute name. Gate on the `class.` key so a
                    // component INPUT sharing a Bootstrap name ([card], [close], [row]) is not mistaken for a class.
                    scanClassList(node.name, node, context);
                }
            },
        };
    },
};
