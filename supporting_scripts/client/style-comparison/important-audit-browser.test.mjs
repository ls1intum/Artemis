import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import importantAudit from './important-audit-browser.mjs';

// The audit runs in the page. These stand-ins give it just enough of the CSSOM to walk a stylesheet and to observe
// that an element changes once a declaration loses its priority.
class CSSStyleRule {}
class CSSMediaRule {}
class CSSContainerRule {}
class CSSLayerBlockRule {}
class CSSSupportsRule {}

const styleRule = (selectorText, property) => {
    const rule = new CSSStyleRule();
    rule.selectorText = selectorText;
    rule.demoted = false;
    rule.style = {
        0: property,
        length: 1,
        [Symbol.iterator]: function* () {
            yield property;
        },
        getPropertyPriority: () => 'important',
        getPropertyValue: () => 'red',
        setProperty: () => {
            rule.demoted = true;
        },
        get cssText() {
            return `${property}: red !important;`;
        },
        set cssText(_) {
            rule.demoted = false;
        },
    };
    return rule;
};

const supportsRule = (conditionText, ...cssRules) => Object.assign(new CSSSupportsRule(), { conditionText, cssRules });

/** Runs the audit on one stylesheet; the element the rules match looks different once the declaration is demoted. */
const audit = (rules, supports) => {
    const element = { tagName: 'DIV', className: 'probe' };
    const inner = (list) => list.flatMap((rule) => (rule instanceof CSSStyleRule ? [rule] : inner(rule.cssRules)));
    globalThis.CSSStyleRule = CSSStyleRule;
    globalThis.CSSMediaRule = CSSMediaRule;
    globalThis.CSSContainerRule = CSSContainerRule;
    globalThis.CSSLayerBlockRule = CSSLayerBlockRule;
    globalThis.CSSSupportsRule = CSSSupportsRule;
    globalThis.CSS = { supports: supports };
    globalThis.matchMedia = () => ({ matches: true });
    globalThis.document = { styleSheets: [{ cssRules: rules }], querySelectorAll: () => [element] };
    globalThis.getComputedStyle = () => ({ getPropertyValue: () => (inner(rules).some((rule) => rule.demoted) ? 'blue' : 'red') });
    return importantAudit();
};

afterEach(() => {
    for (const name of ['CSSStyleRule', 'CSSMediaRule', 'CSSContainerRule', 'CSSLayerBlockRule', 'CSSSupportsRule', 'CSS', 'matchMedia', 'document', 'getComputedStyle']) {
        delete globalThis[name];
    }
});

test('a declaration in a supported @supports block is probed', () => {
    const [result] = audit([supportsRule('(display: grid)', styleRule('.probe', 'color'))], () => true);
    assert.equal(result.matched, 1);
    assert.equal(result.changed, 1);
    assert.equal(result.context, '@supports (display: grid) ');
});

test('a declaration in an @supports block the browser does not support matches nothing', () => {
    const [result] = audit([supportsRule('not (display: grid)', styleRule('.probe', 'color'))], () => false);
    assert.equal(result.matched, 0);
    assert.equal(result.changed, 0);
    assert.equal(result.context, '@supports not (display: grid) ');
});
