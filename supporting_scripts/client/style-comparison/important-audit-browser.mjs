/**
 * Runs in the page: for every `!important` declaration of the loaded stylesheets, whether the computed style of an
 * element it matches changes when the declaration loses its priority. See important-audit.mjs.
 *
 * A declaration is only judged on the elements that exist now. A rule in a media or container query that does not
 * apply, a rule for a state such as `:hover`, and a pseudo-element match nothing here and are reported with zero
 * matches.
 *
 * @param {number} sample the largest number of matching elements to check per rule
 * @returns {{selector: string, property: string, context: string, matched: number, changed: number, example: string}[]}
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

    const judge = (rule, context) => {
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
        for (const property of important) {
            const value = rule.style.getPropertyValue(property);
            const before = elements.map((element) => getComputedStyle(element).getPropertyValue(property));
            rule.style.setProperty(property, value, '');
            const changedElements = elements.filter((element, index) => getComputedStyle(element).getPropertyValue(property) !== before[index]);
            rule.style.setProperty(property, value, 'important');
            results.push({
                selector: sourceSelector(rule.selectorText),
                property,
                context,
                matched: elements.length,
                changed: changedElements.length,
                example: changedElements.length ? describe(changedElements[0]) : '',
            });
        }
    };

    const visit = (rules, context, applies) => {
        for (const rule of rules) {
            if (rule instanceof CSSStyleRule) {
                if (applies) {
                    judge(rule, context);
                } else {
                    for (const property of [...rule.style].filter((name) => rule.style.getPropertyPriority(name) === 'important')) {
                        results.push({ selector: sourceSelector(rule.selectorText), property, context, matched: 0, changed: 0, example: '' });
                    }
                }
            } else if (rule instanceof CSSMediaRule) {
                visit(rule.cssRules, `${context}@media ${rule.conditionText} `, applies && matchMedia(rule.conditionText).matches);
            } else if (rule instanceof CSSLayerBlockRule) {
                visit(rule.cssRules, `${context}@layer ${rule.name} `, applies);
            } else if (rule.cssRules) {
                visit(rule.cssRules, context, applies);
            }
        }
    };

    for (const sheet of document.styleSheets) {
        try {
            visit(sheet.cssRules, '', true);
        } catch {
            // A stylesheet of another origin cannot be read.
        }
    }
    return results;
}
