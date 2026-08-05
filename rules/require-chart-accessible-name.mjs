/**
 * Require every `<p-chart>` to carry an accessible name (or be explicitly hidden from assistive technology).
 *
 * WHY: PrimeNG's chart component renders Chart.js into a `<canvas role="img">`. Everything the chart draws —
 * axis labels, data labels, the values themselves — is rasterised pixels: it cannot be selected, copied, or read.
 * `role="img"` without an accessible name makes a screen reader announce an unlabelled image, so the entire chart
 * is silent. PrimeNG exposes `ariaLabel` / `ariaLabelledBy`, which it forwards to the canvas.
 *
 * SATISFY IT by one of:
 *   - `[ariaLabel]="'some.translation.key' | artemisTranslate"` — preferred; reuse the chart's visible heading key
 *   - `[ariaLabelledBy]="'id-of-the-visible-heading'"` — when the heading has a stable unique id
 *   - `aria-hidden="true"` — ONLY when the chart is decorative, i.e. every figure it shows is also rendered as
 *     text next to it, so hiding it removes nothing and avoids announcing the same number twice
 *
 * Chart.js has no SVG renderer, so a text alternative is the only way to expose chart content to assistive
 * technology; this rule keeps a new chart from silently regressing that.
 *
 * SCOPE: both external templates (`.html`, via the Angular template parser) and inline `template:` strings in
 * `.ts` component files — register the rule for both file globs. An empty name (`ariaLabel=""`) and a negated
 * `aria-hidden="false"` are rejected, since neither yields an accessible name.
 */
const CHART_ELEMENT = 'p-chart';

const NAME_ATTRIBUTES = new Set(['ariaLabel', 'ariaLabelledBy', 'aria-label', 'aria-labelledby']);
const HIDDEN_ATTRIBUTE = 'aria-hidden';

/** True when the attribute name/value pair yields an accessible name, or explicitly hides the chart. */
function isAccessibleTreatment(name, value) {
    if (name === HIDDEN_ATTRIBUTE) {
        // Only `aria-hidden="true"` hides the chart; `false` (or a binding we cannot evaluate) does not.
        return value === undefined || value.trim() === 'true';
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
 * Extracts the raw text of every `<p-chart …>` tag from a template string. Attribute values may contain `>`
 * (`[data]="a > b"`), so the scan tracks quoting instead of stopping at the first `>`.
 */
function extractChartTags(template) {
    const tags = [];
    const opening = `<${CHART_ELEMENT}`;
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
        tags.push({ text: template.slice(index, cursor), offset: index });
        index = template.indexOf(opening, cursor);
    }
    return tags;
}

/** True when a raw `<p-chart …>` tag carries an accessible name or an explicit `aria-hidden="true"`. */
function rawTagIsTreated(tag) {
    for (const name of [...NAME_ATTRIBUTES, HIDDEN_ATTRIBUTE]) {
        // Both the plain and the bound spelling, e.g. `ariaLabel="…"` and `[ariaLabel]="…"`.
        const pattern = new RegExp(`(?<![\\w-])\\[?${name}\\]?\\s*=\\s*("([^"]*)"|'([^']*)')`);
        const match = pattern.exec(tag);
        if (match && isAccessibleTreatment(name, match[2] ?? match[3])) {
            return true;
        }
    }
    return false;
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Require an accessible name (ariaLabel/ariaLabelledBy) or an explicit aria-hidden on every <p-chart>, whose canvas is otherwise an unlabelled role="img".',
        },
        messages: {
            missingAccessibleName:
                '<p-chart> renders a <canvas role="img"> with no accessible name, so screen readers announce an unlabelled image. Add [ariaLabel] (reuse the chart\'s visible heading translation key) or [ariaLabelledBy], or aria-hidden="true" if the chart only restates text shown next to it.',
        },
        schema: [],
    },
    create(context) {
        return {
            // External templates, parsed by @angular-eslint/template-parser.
            Element(node) {
                if (node.name !== CHART_ELEMENT) {
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
                const template = value?.type === 'TemplateLiteral' ? value.quasis.map((quasi) => quasi.value.raw).join(' ') : typeof value?.value === 'string' ? value.value : undefined;
                if (!template) {
                    return;
                }
                for (const tag of extractChartTags(template)) {
                    if (!rawTagIsTreated(tag.text)) {
                        context.report({ node: value, messageId: 'missingAccessibleName' });
                    }
                }
            },
        };
    },
};
