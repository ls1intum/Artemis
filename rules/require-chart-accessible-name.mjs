/**
 * Require every TUM UI chart to carry an accessible name (or be explicitly hidden from assistive technology).
 *
 * WHY: a chart renders into an `<svg role="img">`, and the accessible name is also the caption of the data table
 * the chart renders alongside it for assistive technology. Without a name a screen reader announces an unlabelled
 * image over an uncaptioned table, so the reader is told a chart exists but never what it is about.
 *
 * SATISFY IT by one of:
 *   - `[ariaLabel]="'some.translation.key' | artemisTranslate"` — preferred; reuse the chart's visible heading key
 *   - `[ariaLabelledBy]="'id-of-the-visible-heading'"` — when the heading has a stable unique id
 *   - `aria-hidden="true"` — ONLY when the chart is decorative, i.e. every figure it shows is also rendered as
 *     text next to it, so hiding it removes nothing and avoids announcing the same number twice
 *
 * SCOPE: both external templates (`.html`, via the Angular template parser) and inline `template:` strings in
 * `.ts` component files — register the rule for both file globs. An empty name (`ariaLabel=""`) and a negated
 * `aria-hidden="false"` are rejected, since neither yields an accessible name.
 */
const CHART_ELEMENTS = ['tum-ui-bar-chart', 'tum-ui-line-chart', 'tum-ui-doughnut-chart'];

const NAME_ATTRIBUTES = new Set(['ariaLabel', 'ariaLabelledBy', 'aria-label', 'aria-labelledby']);
const HIDDEN_ATTRIBUTE = 'aria-hidden';

/** True when the attribute name/value pair yields an accessible name, or explicitly hides the chart. */
function isAccessibleTreatment(name, value) {
    if (name === HIDDEN_ATTRIBUTE) {
        // Require the explicit `aria-hidden="true"`. A bare or empty `aria-hidden` is invalid ARIA and behaves
        // inconsistently across assistive technology, and `false` is the opposite of hiding the chart.
        return (value ?? '').trim() === 'true';
    }
    if (!NAME_ATTRIBUTES.has(name)) {
        return false;
    }
    // A bound value (`[ariaLabel]="…"`) is trusted; a static one must not be blank, and a literal empty string
    // in a binding (`[ariaLabel]="''"`) is as useless as no label at all.
    const trimmed = (value ?? '').trim();
    return trimmed !== '' && trimmed !== "''" && trimmed !== '""' && trimmed !== 'undefined' && trimmed !== 'null';
}

/**
 * Extracts the raw text of every chart tag from a template string. Attribute values may contain `>`
 * (`[series]="a > b"`), so the scan tracks quoting instead of stopping at the first `>`.
 */
function extractChartTags(template) {
    const tags = [];
    for (const element of CHART_ELEMENTS) {
        const opening = `<${element}`;
        let index = template.indexOf(opening);
        while (index !== -1) {
            let quote;
            let cursor = index + opening.length;
            while (cursor < template.length) {
                const character = template[cursor];
                if (quote) {
                    if (character === quote) {
                        quote = undefined;
                    }
                } else if (character === '"' || character === "'") {
                    quote = character;
                } else if (character === '>') {
                    break;
                }
                cursor++;
            }
            tags.push({ element, text: template.slice(index, cursor) });
            index = template.indexOf(opening, cursor);
        }
    }
    return tags;
}

/**
 * Splits a raw chart start tag into its attributes. Scanning the tag text as a whole would accept a
 * name-like string that merely sits inside ANOTHER attribute's value, e.g. `pTooltip='[ariaLabel]="Scores"'`.
 */
function parseTagAttributes(tag, element) {
    const attributes = [];
    // name (possibly wrapped in [] or () bindings) optionally followed by ="value" / ='value'.
    const pattern = /([[(]?[\w:.$-]+[\])]?)(\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
    // Skip the element name itself.
    pattern.lastIndex = tag.indexOf(element) + element.length;
    let match;
    while ((match = pattern.exec(tag)) !== null) {
        const rawName = match[1];
        // Strip the Angular binding brackets so `[ariaLabel]` and `ariaLabel` are treated alike.
        const name = rawName.replace(/^[[(]|[\])]$/g, '');
        attributes.push({ name, value: match[4] ?? match[5] ?? match[6] });
    }
    return attributes;
}

/** True when a raw chart tag carries an accessible name or an explicit `aria-hidden="true"`. */
function rawTagIsTreated(tag, element) {
    return parseTagAttributes(tag, element).some((attribute) => isAccessibleTreatment(attribute.name, attribute.value));
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Require an accessible name (ariaLabel/ariaLabelledBy) or an explicit aria-hidden on every TUM UI chart, whose SVG is otherwise an unlabelled role="img".',
        },
        messages: {
            missingAccessibleName:
                'A chart renders an <svg role="img"> and an accompanying data table with no accessible name, so screen readers announce an unlabelled image over an uncaptioned table. Add [ariaLabel] (reuse the chart\'s visible heading translation key) or [ariaLabelledBy], or aria-hidden="true" if the chart only restates text shown next to it.',
        },
        schema: [],
    },
    create(context) {
        return {
            // External templates, parsed by @angular-eslint/template-parser.
            Element(node) {
                if (!CHART_ELEMENTS.includes(node.name)) {
                    return;
                }
                const treated = [...(node.attributes ?? []), ...(node.inputs ?? [])].some((attribute) =>
                    isAccessibleTreatment(attribute.name, attribute.value?.source ?? attribute.value),
                );
                if (!treated) {
                    context.report({ node, messageId: 'missingAccessibleName' });
                }
            },
            // Inline `template:` strings in @Component metadata, parsed by @typescript-eslint/parser. Without this
            // the rule had a blind spot that already let one unlabelled chart through.
            Property(node) {
                if (node.key?.name !== 'template' && node.key?.value !== 'template') {
                    return;
                }
                const value = node.value;
                const template =
                    value?.type === 'TemplateLiteral' ? value.quasis.map((quasi) => quasi.value.raw).join(' ') : typeof value?.value === 'string' ? value.value : undefined;
                if (!template) {
                    return;
                }
                for (const tag of extractChartTags(template)) {
                    if (!rawTagIsTreated(tag.text, tag.element)) {
                        context.report({ node: value, messageId: 'missingAccessibleName' });
                    }
                }
            },
        };
    },
};
