/**
 * Shared scanning of the class names in an Angular template, for the template rules that judge individual class
 * tokens (`no-bootstrap-classes`, `no-ambiguous-spacing-utility`); `no-primeng-component-classes` and
 * `no-raw-tailwind-color-palette` share its notion of what a class list is. Every function is pure (no ESLint context), so
 * tools such as supporting_scripts/migration/migrate.mjs can reuse the exact same extraction.
 *
 * Each token comes with its offset in the scanned text, so a rule can point at, or rewrite, just that token.
 */

// `class` plus PrimeNG class inputs (`styleClass` and component-specific `*StyleClass` like `contentStyleClass`),
// which render their classes onto the host at runtime. These hold a raw class list; `[ngClass]` holds an expression.
export function isClassListAttribute(name) {
    return name === 'class' || name === 'styleClass' || name.endsWith('StyleClass');
}

// Matches single- or double-quoted string literals (e.g. the keys of `[ngClass]="{ 'btn': x }"` or the
// segments of `[class]="'d-flex ' + x"`).
const STRING_LITERAL = /'([^']*)'|"([^"]*)"/dg;

// Matches an UNQUOTED object-literal key, e.g. the `btn` of `[ngClass]="{ btn: active }"`. Prettier rewrites a
// quoted `{ 'btn': x }` to this unquoted form, so without this the simplest Bootstrap key bypasses the rule after
// formatting. A key is an identifier directly after `{` or `,` and before `:`; hyphenated class names (`btn-lg`)
// are not valid unquoted identifiers, so they stay quoted and are covered by STRING_LITERAL.
const UNQUOTED_OBJECT_KEY = /[{,]\s*([A-Za-z_$][\w$]*)\s*:/dg;

// Matches a backtick template literal, e.g. the `` `btn ${extra}` `` of `[class]="`btn ${extra}`"`. Only its static
// chunks carry literal class names; the `${…}` interpolations are dynamic expressions (string literals inside them
// are already covered by STRING_LITERAL), so they are blanked before scanning.
const TEMPLATE_LITERAL = /`([^`]*)`/dg;

// Stands in for a dynamic part (an interpolation) of a class list. It is a private-use character, which no class
// name contains, and it keeps the offsets of the text.
const DYNAMIC = '\uE000';

// A blanked interpolation stays part of its non-whitespace run, so a literal glued to it (`btn-${x}`, `${x}mb-3`) is
// one token with the placeholder in it, and `classTokens` leaves that token out: its class name is not known.
function blank(text, pattern) {
    return text.replace(pattern, (match) => DYNAMIC.repeat(match.length));
}

/**
 * The whitespace-separated tokens of a raw class list, e.g. a static `class="..."`, with their offsets in `text`. A token
 * that is glued to a blanked interpolation is left out, because the interpolation decides which class it is.
 */
export function classTokens(text) {
    if (typeof text !== 'string') {
        return [];
    }
    return Array.from(text.matchAll(/\S+/g), (match) => ({ token: match[0], index: match.index })).filter(({ token }) => !token.includes(DYNAMIC));
}

/** The class tokens of a static class list that contains Angular interpolations (`class="a-{{ x }} b"`), interpolations blanked. */
export function classTokensInInterpolatedList(text) {
    return classTokens(typeof text === 'string' ? blank(text, /\{\{[\s\S]*?\}\}/g) : text);
}

// The bound source of `[class]` / `[ngClass]` is an Angular expression, not a class list. Class names live in string
// literals (`'btn'`), unquoted object keys (`{ btn: x }`), or the static chunks of a template literal (`` `btn ${x}` ``).
/** The class tokens of such an expression, with their offsets in `source`. */
export function classTokensInBindingExpression(source) {
    if (typeof source !== 'string') {
        return [];
    }
    const found = [];
    const collect = (text, offset) => {
        for (const { token, index } of classTokens(text)) {
            found.push({ token, index: offset + index });
        }
    };
    for (const match of source.matchAll(STRING_LITERAL)) {
        const group = match[1] !== undefined ? 1 : 2;
        collect(match[group], match.indices[group][0]);
    }
    for (const match of source.matchAll(UNQUOTED_OBJECT_KEY)) {
        collect(match[1], match.indices[1][0]);
    }
    for (const match of source.matchAll(TEMPLATE_LITERAL)) {
        collect(blank(match[1], /\$\{[^}]*\}/g), match.indices[1][0]);
    }
    return found;
}
