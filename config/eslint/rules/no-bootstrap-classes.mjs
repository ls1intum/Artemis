/**
 * Forbid Bootstrap CSS classes in templates.
 * Bootstrap is removed from the client, so these classes no longer style anything. The rule keeps them from coming
 * back, for example through a merged branch or copied markup, and points to the replacement.
 *
 * Only classes that are UNAMBIGUOUSLY Bootstrap-only are banned, that is they do not collide with a valid
 * Tailwind v4 utility of the same name. In particular the spacing scale (`mb-3`, `me-1`, `p-3`, `gap-2`)
 * and logical-margin utilities (`ms-*`, `me-*`) are NOT banned: they are legitimate Tailwind utilities.
 * Bootstrap gave the steps 3 to 5 other values (16, 24 and 48px against Tailwind's 12, 16 and 20px).
 * Color application is governed separately by `no-raw-tailwind-color-palette` (templates) and the stylelint
 * hex/`--bs-` ban (SCSS).
 *
 * Scope: static `class="..."`, `[class.<bootstrap-class>]`, and the bound source of `[class]` / `[ngClass]`.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */

import { classTokens, classTokensInBindingExpression, isClassListAttribute } from './class-list.mjs';

// Each entry matches a single, whole class token (anchored). Grouped by Bootstrap concept with its
// TUM AET UI / Tailwind replacement noted for the reviewer.
const BANNED = [
    // Buttons -> tumaet-ui-button / tumaet-ui-button-group
    /^btn(-.+)?$/,
    // Badges -> tumaet-ui-tag
    /^badge$/,
    // Cards -> tumaet-ui-card (or a Tailwind `rounded-lg border p-4` box)
    /^card(-.+)?$/,
    // Alerts / callouts -> tumaet-ui-message. Only Bootstrap's own alert classes — not custom `alert-*` names
    // (e.g. the alert overlay's `alert-wrap` / `alert-inner` / `alert-animation-wrapper`).
    /^alert(-(primary|secondary|success|danger|warning|info|light|dark|dismissible|link|heading))?$/,
    // Form controls -> TUM AET UI inputs (tumAetUiInput / tumaet-ui-select / tumAetUiTextarea / tumaet-ui-checkbox / tumaet-ui-radio-button)
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
    // Bootstrap greyscale text colors -> text-muted-color / semantic token. `text-primary`/`text-white`/
    // `text-black` are NOT listed: they are also Tailwind utilities (`text-primary` is defined in tailwind.css).
    /^text-(secondary|light|dark)$/,
    // Bootstrap semantic state colors (text/border/bg) -> named state tokens, e.g. text-state-danger,
    // bg-state-success, border-state-warning, text-state-info (the arbitrary `text-(--danger)` form is itself
    // banned by no-raw-tailwind-color-palette). Only danger/success/warning/info are matched: those are
    // Bootstrap-only, whereas `*-primary` / `*-surface-*` are colours that tailwind.css defines and stay allowed.
    /^(text|border|bg)-(danger|success|warning|info)$/,
    // Bare `table` and the `collapse` family double as Tailwind utility NAMES, and tailwind.css blocklists them
    // from generation (`@source not inline("table"/"collapse")`). Because the Tailwind versions can never
    // generate, a `table`/`collapse*` in a template styles nothing: ban them here so the lint stays symmetric
    // with the @source blocklist.
    /^table$/,
    /^(collapse|collapse-horizontal|collapsing)$/,
    // Table component modifiers -> tumaet-ui-table / tumAetUiTable (Tailwind's `table-cell`/`table-fixed`/`table-auto` are NOT matched)
    /^table-(striped|striped-columns|bordered|borderless|hover|active|sm|responsive|responsive-(sm|md|lg|xl|xxl)|group-divider)$/,
    // Bootstrap percentage sizing -> Tailwind `h-full`/`w-full` or an explicit Tailwind size. These collide:
    // Bootstrap's `h-100` meant `height: 100%`, Tailwind's `h-100` is 25rem, so a stray `h-100` written with
    // Bootstrap in mind silently renders the wrong size. Only 25/50/75/100 are matched; `h-auto`/`w-auto` are shared (width:auto) and stay.
    /^[hw]-(25|50|75|100)$/,
    // Bootstrap component families with no Tailwind-utility name clash. Each has a TUM AET UI replacement
    // (modal -> tumaet-ui-dialog, dropdown -> tumaet-ui-menu, nav -> tumaet-ui-tabs, etc.). Anchored whole-token, so
    // a custom class that merely contains the word (e.g. `my-modal-wrapper`) is unaffected.
    /^modal(-.+)?$/,
    /^dropdown(-.+)?$/,
    /^navbar(-.+)?$/,
    // `nav` is matched by EXACT Bootstrap class only, not the `nav-*` prefix — custom names like the admin
    // sidebar's `nav-group-header` / `nav-link-sidebar` (styled by its own SCSS) must not be flagged.
    /^nav$/,
    /^nav-(link|item|tabs|pills|fill|justified|underline)$/,
    /^list-group(-.+)?$/,
    /^spinner-(border|grow)(-sm)?$/,
    /^accordion(-.+)?$/,
    /^breadcrumb(-.+)?$/,
    /^(pagination|page-link|page-item)$/,
    /^toast(-.+)?$/,
    /^popover(-.+)?$/,
    /^carousel(-.+)?$/,
    /^offcanvas(-.+)?$/,
    // Misc Bootstrap-only widgets / utilities
    /^(close|btn-close)$/,
    /^visually-hidden(-focusable)?$/,
    /^text-truncate$/, // -> Tailwind `truncate`
    // Typography helpers -> Tailwind `font-bold`, `italic`, `text-sm`, `uppercase`, `no-underline`, `break-words`, ...
    /^fw-(light|lighter|normal|medium|semibold|bold|bolder)$/,
    /^fst-(italic|normal)$/,
    /^fs-[1-6]$/,
    /^lh-(1|sm|base|lg)$/,
    /^(h[1-6]|display-[1-6]|lead|small|mark|blockquote|blockquote-footer)$/,
    /^text-(break|monospace|uppercase|lowercase|capitalize|decoration-(none|underline|line-through))$/,
    /^font-(monospace|italic)$/,
    // Background and text colour helpers with no brand-bound Tailwind equivalent -> semantic tokens
    /^bg-(light|dark|body(-secondary|-tertiary)?|gradient)$/,
    /^(text|bg|border|link)-(primary|secondary|success|danger|warning|info|light|dark)-(subtle|emphasis)$/,
    /^(bg|text|border)-opacity-(10|25|50|75|100)$/,
    /^link-(primary|secondary|success|danger|warning|info|light|dark|body-emphasis|underline.*|offset-.+|opacity.*)$/,
    // Borders and rounding -> Tailwind `rounded-lg`, `rounded-full`, `border-t`, ...
    /^rounded-(circle|pill|top|bottom|start|end)(-[0-5])?$/,
    /^rounded-[0-5]$/,
    /^border-(top|bottom|start|end)(-0)?$/,
    // Sizing and positioning. `top-50` and `h-100` collide with a Tailwind spacing utility of the same name.
    /^(mw|mh|vw|vh|min-vw|min-vh)-100$/,
    /^(top|bottom|start|end)-(50|100)$/,
    /^position-(static|relative|absolute|fixed|sticky)$/,
    /^translate-middle(-x|-y)?$/,
    /^(sticky|fixed)-(top|bottom)$/,
    /^sticky-(sm|md|lg|xl|xxl)-(top|bottom)$/,
    /^(clearfix|stretched-link|ratio|hstack|vstack|vr)$/,
    /^ratio-.+$/,
    // Layout helpers. Bare `container` exists in Tailwind too, but never generates the Bootstrap behaviour.
    /^container(-fluid|-(sm|md|lg|xl|xxl))?$/,
    /^row-cols(-(sm|md|lg|xl|xxl))?-([1-6]|auto)$/,
    /^offset(-(sm|md|lg|xl|xxl))?-([0-9]|1[01])$/,
    /^flex-(sm|md|lg|xl|xxl)-(row|column|row-reverse|column-reverse|wrap|nowrap|fill|grow-[01]|shrink-[01])$/,
    /^order-(sm|md|lg|xl|xxl)-.+$/,
    /^[mp][trblxyse]?-n[1-5]$/, // negative margins -> `-mb-4`
    /^d-print-.+$/,
    /^user-select-(all|auto|none)$/,
    /^pe-(none|auto|all)$/,
    // Media, lists, tables and validation state
    /^img-(fluid|thumbnail)$/,
    /^list-(inline|inline-item|unstyled)$/,
    /^figure(-.+)?$/,
    /^table-(primary|secondary|success|danger|warning|info|light|dark)$/,
    /^thead-(light|dark)$/,
    /^(is-valid|is-invalid|valid-tooltip|invalid-tooltip|was-validated|needs-validation)$/,
];

export function isBanned(token) {
    return BANNED.some((re) => re.test(token));
}

function scanClassList(text, node, context) {
    for (const { token } of classTokens(text)) {
        if (isBanned(token)) {
            context.report({ node, messageId: 'bootstrapClass', data: { cls: token } });
        }
    }
}

// Returns the banned tokens found in the bound source of a `[class]` / `[ngClass]` binding. Pure (no ESLint context)
// so other tools can reuse the exact same binding extraction.
export function bannedClassesInBindingExpression(source) {
    return classTokensInBindingExpression(source)
        .map(({ token }) => token)
        .filter(isBanned);
}

function scanBindingExpression(source, node, context) {
    for (const cls of bannedClassesInBindingExpression(source)) {
        context.report({ node, messageId: 'bootstrapClass', data: { cls } });
    }
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid classes of the removed Bootstrap library in templates; use Tailwind utilities + TUM AET UI components instead.',
        },
        messages: {
            bootstrapClass:
                "Bootstrap class '{{cls}}' has no effect, Bootstrap is removed from the client: use a Tailwind utility or a TUM AET UI component (btn->tumaet-ui-button, badge->tumaet-ui-tag, alert->tumaet-ui-message, card->tumaet-ui-card, form-control->tumAetUiInput, row/col->grid|flex, d-flex->flex). See client-development.mdx (### Styling).",
        },
        schema: [],
    },
    create(context) {
        // `styleClass`-like inputs render their classes onto the host at runtime, so Bootstrap in them must be caught too.
        return {
            TextAttribute(node) {
                if (isClassListAttribute(node.name)) {
                    scanClassList(node.value, node, context);
                }
            },
            BoundAttribute(node) {
                if (isClassListAttribute(node.name) || node.name === 'ngClass') {
                    // `[class]`/`[ngClass]`/`[styleClass]` bind an expression (object map or string concat), not a
                    // raw class list — scan the string literals inside it.
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
