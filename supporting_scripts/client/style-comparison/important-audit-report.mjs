// The part of the `!important` audit that needs no browser: which observation belongs to which declaration of the
// source, and in which group of the report a declaration ends up. See important-audit.mjs.

/** Shorthands whose longhands do not start with the name of the shorthand. */
const LONGHANDS = {
    font: /^line-height$/,
    inset: /^(?:top|right|bottom|left)$/,
    overflow: /^overflow-[xy]$/,
    gap: /^(?:row|column)-gap$/,
    'border-radius': /^border-.+-radius$/,
    'border-color': /^border-.+-color$/,
    'border-width': /^border-.+-width$/,
    'border-style': /^border-.+-style$/,
    'place-items': /^(?:align|justify)-items$/,
    'place-content': /^(?:align|justify)-content$/,
    'grid-area': /^grid-(?:row|column)-(?:start|end)$/,
};
const covers = (property, longhand) => longhand === property || longhand.startsWith(`${property}-`) || LONGHANDS[property]?.test(longhand) === true;

/** One spelling for a selector of the source and for the one the browser reports for it. */
export const normalise = (selector) =>
    selector
        .replace(/::ng-deep/g, ' ')
        .replace(/:host\(([^()]*(?:\([^()]*\))?[^()]*)\)/g, '$1:host')
        .replace(/"/g, "'")
        .replace(/\s*([>+~,])\s*/g, '$1')
        .replace(/\s+/g, ' ')
        .trim();

/**
 * Adds what the page reported for one visited page to the observations, one per selector and property.
 * An observation remembers the rules it comes from: the same selector and property in another media query or in
 * the stylesheet of another component is another declaration, and the observation cannot tell them apart.
 *
 * @param {Map<string, object>} observations the observations so far
 * @param {{selector: string, scope: string, property: string, context: string, matched: number, changed: number, example: string}[]} entries
 * @param {string} name the visited page
 */
export function observe(observations, entries, name) {
    for (const entry of entries) {
        const selector = normalise(entry.selector);
        const key = `${selector}|${entry.property}`;
        const total = observations.get(key) ?? { selector, property: entry.property, matched: 0, changed: 0, example: '', origins: new Set() };
        total.origins.add(`${entry.context}|${entry.scope}`);
        total.matched = Math.max(total.matched, entry.matched);
        total.changed += entry.changed;
        if (entry.changed && !total.example) total.example = `${entry.example} on ${name}`;
        observations.set(key, total);
    }
}

/**
 * Sorts the declarations of the source into the groups of the report.
 *
 * A declaration is `ambiguous` when its selector and property occur more than once in the source or in the rules of
 * the page: the observations of the rules are then combined, and a rule that changes nothing could hide behind a
 * rule that matches elements. These declarations are not judged.
 *
 * @param {{file: string, line: number, selector: string, property: string}[]} declarations
 * @param {Map<string, object>} observations
 * @returns {Record<string, string[]>} the rows of each group
 */
export function classify(declarations, observations) {
    const groups = { 'changes nothing': [], needed: [], 'matched no element': [], ambiguous: [] };
    const all = [...observations.values()];
    for (const declaration of declarations) {
        const entries = all.filter((entry) => entry.selector === declaration.selector && covers(declaration.property, entry.property));
        const siblings = declarations.filter(
            (other) => other.selector === declaration.selector && (covers(declaration.property, other.property) || covers(other.property, declaration.property)),
        );
        const origins = new Set(entries.flatMap((entry) => [...entry.origins]));
        const matched = Math.max(0, ...entries.map((entry) => entry.matched));
        const changed = entries.reduce((sum, entry) => sum + entry.changed, 0);
        const example = entries.find((entry) => entry.example)?.example ?? '';
        // `calm()` switches animations and transitions off, so the priority of these declarations cannot be judged.
        const judged = matched > 0 && !/^(?:animation|transition)/.test(declaration.property);
        const group = siblings.length > 1 || origins.size > 1 ? 'ambiguous' : !judged ? 'matched no element' : changed ? 'needed' : 'changes nothing';
        groups[group].push(`${declaration.file}:${declaration.line}  ${declaration.selector} { ${declaration.property} }${example ? `  (${example})` : ''}`);
    }
    return groups;
}
