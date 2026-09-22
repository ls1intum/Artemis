import stylelint from 'stylelint';
import selectorParser from 'postcss-selector-parser';
import resolveNestedSelector from 'postcss-resolve-nested-selector';

const ruleName = 'design-system/no-private-classes';

/** Decoded CSS identifiers, including selectors inside :not/:has, still depend on private markup. */
export function checkPrivateClasses(root, prefix, report) {
    const reported = new Set();
    const check = (text, node) => {
        for (let parent = node; parent; parent = parent.parent) if (reported.has(parent)) return;
        let selectors;
        try {
            selectors = selectorParser().astSync(text);
        } catch {
            return; // Dynamic Sass selectors cannot be resolved without executing Sass.
        }
        let found = false;
        selectors.walk((selector) => {
            if (selector.type === 'class' && selector.value.startsWith(prefix)) found = true;
            if (selector.type !== 'attribute' || selector.attribute.toLowerCase() !== 'class' || !selector.value) return;
            const value = selector.insensitive ? selector.value.toLowerCase() : selector.value;
            const reserved = selector.insensitive ? prefix.toLowerCase() : prefix;
            if (value.split(/\s+/).some((name) => name.startsWith(reserved))) found = true;
        });
        if (found) {
            reported.add(node);
            report({ message: `Do not select private ${prefix} classes. Use public component inputs or change the package.`, node });
        }
    };
    root.walkRules((node) => {
        for (const selector of resolveNestedSelector(node.selector, node)) check(selector, node);
    });
    root.walkAtRules((node) => {
        if (node.name === 'extend' || node.name === 'at-root') {
            const selector = node.name === 'extend' && node.params.endsWith('!optional') ? node.params.slice(0, -'!optional'.length).trimEnd() : node.params;
            check(selector, node);
        }
    });
}

const rule = (prefix) => (root, result) => {
    if (!stylelint.utils.validateOptions(result, ruleName, { actual: prefix, possible: (value) => typeof value === 'string' && value.length > 0 })) return;
    checkPrivateClasses(root, prefix, (descriptor) => stylelint.utils.report({ ...descriptor, result, ruleName }));
};
rule.ruleName = ruleName;
export default stylelint.createPlugin(ruleName, rule);
