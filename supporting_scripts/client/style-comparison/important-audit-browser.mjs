/**
 * Runs in the page: for every `!important` declaration of the loaded stylesheets, whether the computed style of an
 * element it matches changes when the declaration loses its priority. See important-audit.mjs.
 *
 * A declaration is only judged on the elements that exist now. A rule in a media query that does not apply, a rule
 * for a state such as `:hover`, and a pseudo-element match nothing here and are reported with zero matches. So is a
 * declaration whose value the page cannot read back, such as the longhands of `padding: var(--gap)`: they read as an
 * empty string, and writing that string back would delete them.
 *
 * Whether a container query applies depends on the query container of each element, which the page does not
 * expose. A declaration in a container query is therefore only reported when it changes an element. If it changes
 * none, it is reported with zero matches, because it may just not apply.
 *
 * `selector` is the selector as it is written in the source; `scope` is the selector as the browser holds it, with
 * the attributes of the view encapsulation, so that rules of different components stay apart.
 *
 * @param {number} sample the largest number of matching elements to check per rule
 * @returns {{selector: string, scope: string, property: string, context: string, matched: number, changed: number, example: string}[]}
 */
export default function importantAudit(sample = 40) {
    const results = [];

    /** The selector as it is written in the source: without the attributes of the view encapsulation of Angular. */
    const sourceSelector = (selector) =>
        selector
            .replace(/\[_nghost-[\w-]+\]/g, ':host')
            .replace(/\[_ngcontent-[\w-]+\]/g, '')
            .replace(/\s+/g, ' ')
            .trim();

    const describe = (element) => {
        const name = typeof element.className === 'string' ? element.className.trim().split(/\s+/).slice(0, 3).join('.') : '';
        return element.tagName.toLowerCase() + (name ? `.${name}` : '');
    };

    const judge = (rule, context, conditional) => {
        const important = [...rule.style].filter((property) => rule.style.getPropertyPriority(property) === 'important');
        if (!important.length) {
            return;
        }
        let elements = [];
        try {
            elements = [...document.querySelectorAll(rule.selectorText)].slice(0, sample);
        } catch {
            // A selector that only a stylesheet may contain, for example one with a pseudo-element.
        }
        const declarations = rule.style.cssText;
        for (const property of important) {
            const value = rule.style.getPropertyValue(property);
            const result = { selector: sourceSelector(rule.selectorText), scope: rule.selectorText, property, context, matched: 0, changed: 0, example: '' };
            results.push(result);
            if (!value) {
                continue;
            }
            const before = elements.map((element) => getComputedStyle(element).getPropertyValue(property));
            try {
                rule.style.setProperty(property, value, '');
                const changedElements = elements.filter((element, index) => getComputedStyle(element).getPropertyValue(property) !== before[index]);
                result.matched = conditional && !changedElements.length ? 0 : elements.length;
                result.changed = changedElements.length;
                result.example = changedElements.length ? describe(changedElements[0]) : '';
            } finally {
                rule.style.cssText = declarations;
            }
        }
    };

    const visit = (rules, context, applies, conditional) => {
        for (const rule of rules) {
            if (rule instanceof CSSStyleRule) {
                if (applies) {
                    judge(rule, context, conditional);
                } else {
                    for (const property of [...rule.style].filter((name) => rule.style.getPropertyPriority(name) === 'important')) {
                        results.push({ selector: sourceSelector(rule.selectorText), scope: rule.selectorText, property, context, matched: 0, changed: 0, example: '' });
                    }
                }
            } else if (rule instanceof CSSMediaRule) {
                visit(rule.cssRules, `${context}@media ${rule.conditionText} `, applies && matchMedia(rule.conditionText).matches, conditional);
            } else if (rule instanceof CSSContainerRule) {
                visit(rule.cssRules, `${context}@container ${rule.conditionText} `, applies, true);
            } else if (rule instanceof CSSLayerBlockRule) {
                visit(rule.cssRules, `${context}@layer ${rule.name} `, applies, conditional);
            } else if (rule.cssRules) {
                visit(rule.cssRules, context, applies, conditional);
            }
        }
    };

    for (const sheet of document.styleSheets) {
        try {
            visit(sheet.cssRules, '', true, false);
        } catch {
            // A stylesheet of another origin cannot be read.
        }
    }
    return results;
}
