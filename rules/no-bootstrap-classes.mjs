/**
 * Forbid Bootstrap CSS classes in templates of modules that have been migrated to Tailwind + PrimeNG.
 * This is the regression lock for the incremental Bootstrap → PrimeNG/Tailwind migration: once a module's
 * templates are clean, this rule (scoped to that module in eslint.config.mjs) prevents Bootstrap classes
 * from creeping back in.
 *
 * Only classes that are UNAMBIGUOUSLY Bootstrap-only are banned — i.e. they do not collide with a valid
 * Tailwind v4 utility of the same name. In particular the spacing scale (`mb-3`, `me-1`, `p-3`, `gap-2`)
 * and logical-margin utilities (`ms-*`, `me-*`) are NOT banned: Bootstrap and Tailwind share those names
 * 1:1, so they are legitimate Tailwind utilities after migration. Color application is governed separately
 * by `no-raw-tailwind-color-palette` (templates) and the stylelint hex/`--bs-` ban (SCSS).
 *
 * Scope: static `class="..."`, `[class.<bootstrap-class>]`, and the bound source of `[class]` / `[ngClass]`.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */

// Each entry matches a single, whole class token (anchored). Grouped by Bootstrap concept with its
// PrimeNG / Tailwind replacement noted for the reviewer.
const BANNED = [
    // Buttons -> p-button / p-buttonGroup
    /^btn(-.+)?$/,
    // Badges -> p-tag
    /^badge$/,
    // Cards -> p-card (or a Tailwind `rounded-lg border p-4` box)
    /^card(-.+)?$/,
    // Alerts / callouts -> p-message
    /^alert(-.+)?$/,
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
    // Bootstrap semantic state colors (text/border/bg) -> brand tokens, e.g. text-(--p-red-500),
    // text-(--p-green-500), text-(--p-yellow-500), text-(--p-blue-500). Only danger/success/warning/info are
    // matched: those are Bootstrap-only, whereas `*-primary` / `*-surface-*` are PrimeNG tokens and stay allowed.
    /^(text|border|bg)-(danger|success|warning|info)$/,
    // Table component modifiers -> p-table (Tailwind's display `table` / `table-cell` are NOT matched)
    /^table-(striped|striped-columns|bordered|borderless|hover|active|sm|responsive|responsive-(sm|md|lg|xl|xxl)|group-divider)$/,
    // Misc Bootstrap-only widgets / utilities
    /^(close|btn-close)$/,
    /^visually-hidden(-focusable)?$/,
    /^text-truncate$/, // -> Tailwind `truncate`
];

function isBanned(token) {
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
// segments of `[class]="'d-flex ' + x"`). Class names only ever live inside string literals in a binding
// expression, so scanning the literal contents avoids false positives from identifiers/operators.
const STRING_LITERAL = /'([^']*)'|"([^"]*)"/g;

// The bound source of `[class]` / `[ngClass]` is an Angular expression, not a class list. Extract its
// string literals and scan their whitespace-separated tokens with the same per-token isBanned() check.
function scanBindingExpression(source, node, context) {
    if (typeof source !== 'string') {
        return;
    }
    STRING_LITERAL.lastIndex = 0;
    let match;
    while ((match = STRING_LITERAL.exec(source)) !== null) {
        scanClassList(match[1] ?? match[2], node, context);
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
                } else if (typeof node.name === 'string') {
                    // [class.btn]="..." -> the class token is the attribute name
                    scanClassList(node.name, node, context);
                }
            },
        };
    },
};
