/**
 * Forbid hand-writing a PrimeNG component ROOT class (e.g. `class="p-button"`, `class="p-inputtext"`) onto an
 * element in a template.
 *
 * WHY: PrimeNG (v20+) injects each component's CSS into <head> LAZILY — only when the first real instance of that
 * component renders (via its `ngOnInit`/`BaseComponent`), once per SPA session. A bare element that merely carries
 * the root class (`<button class="p-button">`, `<div class="p-inputtext">`) never runs that lifecycle, so its
 * styling is non-deterministic: it appears unstyled/grey/oversized until — and only if — some real component of the
 * same type happens to render first, and it can stretch neighbouring elements. Render the REAL component instead
 * (`<button pButton>`, `<input pInputText>`, `<p-tag>`, …), which self-injects its CSS deterministically. For a
 * custom element that should merely LOOK like a PrimeNG surface, use Tailwind utilities + PrimeNG tokens
 * (`border rounded-md px-3 py-2` with `border-color`/`background` tokens), not the component class.
 *
 * SCOPE: only the BARE root class is flagged (exact match). Modifier classes on a REAL component — e.g.
 * `class="p-button-secondary p-button-sm"` on a `<button pButton>` — are NOT flagged: the directive adds the root
 * class and self-injects the CSS, so the modifiers are harmless (prefer the `severity`/`size` inputs, but that is a
 * separate concern). Matched in static `class="..."`, `[class.<root>]`, and the bound source of `[class]`/`[ngClass]`.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */

// PrimeNG component ROOT classes that the component itself applies and whose CSS is lazily injected. Hand-writing
// any of these fakes a component. Curated to visually-fakeable, styled components; deliberately excludes shared
// helper classes (p-component, p-disabled, p-error, p-fluid, p-highlight, p-ripple, p-link, …) which are not roots.
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
            description:
                'Forbid hand-writing PrimeNG component root classes onto elements; render the real PrimeNG component (its CSS is injected lazily, so a bare class renders non-deterministically).',
        },
        messages: {
            handPaintedRoot:
                "Hand-written PrimeNG root class '{{cls}}' is not allowed — PrimeNG injects component CSS lazily, so a bare element carrying this class renders non-deterministically. Render the real component instead (e.g. <button pButton>, <input pInputText>, <p-tag>), or style a custom element with Tailwind utilities + PrimeNG tokens. See client-development.mdx (### Styling).",
        },
        schema: [],
    },
    create(context) {
        return {
            // Static class="...".
            TextAttribute(node) {
                if (node.name === 'class') {
                    scan(node.value, node, context);
                }
            },
            // [class.p-button]="x" (root is the attribute name); [class]/[ngClass] (root is in the bound source).
            BoundAttribute(node) {
                if (node.name === 'class' || node.name === 'ngClass') {
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
