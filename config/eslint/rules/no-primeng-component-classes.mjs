/**
 * Forbid a PrimeNG component ROOT class (e.g. `class="p-button"`, `class="p-inputtext"`) on an element in a template.
 *
 * WHY: PrimeNG is removed from the client, so these classes style nothing. An element that carries one renders
 * unstyled. The rule keeps such markup from coming back, for example through a merged branch or copied markup. Use
 * the TUM AET UI component or directive instead (`<button tumAetUiButton>`, `<input tumAetUiInput>`,
 * `<tumaet-ui-tag>`, ...), or Tailwind utilities with semantic tokens for a custom element.
 *
 * SCOPE: only the BARE root class is flagged (exact match), not modifier classes such as `p-button-secondary`.
 * Matched in static `class="..."`, `[class.<root>]`, and the bound source of `[class]`/`[ngClass]`.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */
import { isClassListAttribute } from './class-list.mjs';

// Root classes of the PrimeNG components that Artemis used. The list is hand-curated and deliberately excludes shared
// helper classes (p-component, p-disabled, p-error, p-fluid, p-highlight, p-ripple, p-link, …), which are not roots.
const PRIMENG_ROOTS = [
    'p-button',
    'p-inputtext',
    'p-textarea',
    'p-checkbox',
    'p-radiobutton',
    'p-togglebutton',
    'p-selectbutton',
    'p-tag',
    'p-message',
    'p-badge',
    'p-chip',
    'p-card',
    'p-panel',
    'p-fieldset',
    'p-progressbar',
    'p-avatar',
    'p-divider',
    'p-toolbar',
    'p-select',
    'p-multiselect',
    'p-datepicker',
    'p-autocomplete',
    'p-inputnumber',
    'p-slider',
    'p-rating',
    'p-listbox',
];

// A root class at a class boundary, NOT followed by `-` (so modifiers like `p-button-sm`/`p-button-secondary`
// are not matched) and not part of a longer word. The `g` flag reports every offending class in an attribute.
const ROOT_CLASS = new RegExp(`(?<![\\w-])(?:${PRIMENG_ROOTS.join('|')})(?![\\w-])`, 'g');

function scan(text, node, context) {
    if (typeof text !== 'string') {
        return;
    }
    ROOT_CLASS.lastIndex = 0;
    let match;
    while ((match = ROOT_CLASS.exec(text)) !== null) {
        context.report({ node, messageId: 'handPaintedRoot', data: { cls: match[0] } });
    }
}

export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid root classes of the removed PrimeNG library on elements; use the TUM AET UI component or Tailwind utilities instead.',
        },
        messages: {
            handPaintedRoot:
                "PrimeNG root class '{{cls}}' has no effect, PrimeNG is removed from the client. Use the TUM AET UI component instead (e.g. <button tumAetUiButton>, <input tumAetUiInput>, <tumaet-ui-tag>), or style a custom element with Tailwind utilities and semantic tokens. See client-development.mdx (### Styling).",
        },
        schema: [],
    },
    create(context) {
        // `class` plus class inputs (`styleClass`, component-specific `*StyleClass`), which render their value onto
        // an element as well.
        return {
            // Static class="..." / styleClass="...".
            TextAttribute(node) {
                if (isClassListAttribute(node.name)) {
                    scan(node.value, node, context);
                }
            },
            // [class.p-button]="x" (root is the attribute name); [class]/[ngClass]/[styleClass] (root in the source).
            BoundAttribute(node) {
                if (isClassListAttribute(node.name) || node.name === 'ngClass') {
                    scan(node.value?.source, node, context);
                } else if (node.keySpan?.details?.startsWith('class.')) {
                    // [class.p-button]="x" — the root is the attribute name. Gate on the `class.` key so a
                    // component INPUT that happens to share the name is not scanned as a class.
                    scan(node.name, node, context);
                }
            },
        };
    },
};
