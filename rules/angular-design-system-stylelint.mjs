import stylelint from 'stylelint';
import postcss from 'postcss';
import selectorParser from 'postcss-selector-parser';
import resolveNestedSelector from 'postcss-resolve-nested-selector';
import { plugin as engine } from './design-system/plugin.mjs';
import { createComponentIndex } from './angular-design-system-project.mjs';

const ruleName = 'design-system/no-restyle';

// Only the last compound selects the declaration's subject. :not/:has describe exclusions or relatives.
function subjects(selector) {
    const nodes = selector.nodes.slice(selector.nodes.findLastIndex((node) => node.type === 'combinator') + 1);
    let result = [{ name: undefined, attributes: [], inputs: [] }];
    for (const node of nodes) {
        if (node.type === 'tag')
            result = result.filter((entry) => !entry.name || entry.name === node.value.toLowerCase()).map((entry) => ({ ...entry, name: node.value.toLowerCase() }));
        else if (node.type === 'attribute') result.forEach((entry) => entry.attributes.push({ name: node.attribute, value: node.operator === '=' ? node.value : '' }));
        else if (node.type === 'class') result.forEach((entry) => entry.attributes.push({ name: 'class', value: node.value }));
        else if (node.type === 'pseudo') {
            const pseudo = node.value.toLowerCase();
            let alternatives = [':is', ':where'].includes(pseudo) ? node.nodes : undefined;
            // Only an exact double negation is positive; lists outside the inner :not are intersections.
            const inner = node.nodes?.length === 1 && node.nodes[0].nodes.length === 1 ? node.nodes[0].nodes[0] : undefined;
            if (pseudo === ':not' && inner?.type === 'pseudo' && inner.value.toLowerCase() === ':not') alternatives = inner.nodes;

            if ([':nth-child', ':nth-last-child'].includes(pseudo)) {
                const first = node.nodes[0];
                const of = first?.nodes.findIndex((part) => part.type === 'tag' && part.value.toLowerCase() === 'of') ?? -1;
                if (of > 0) alternatives = [{ nodes: first.nodes.slice(of + 1) }, ...node.nodes.slice(1)];
            }
            if (!alternatives) continue;
            result = result.flatMap((entry) =>
                alternatives
                    .flatMap(subjects)
                    .filter((alternative) => !entry.name || !alternative.name || entry.name === alternative.name)
                    .map((alternative) => ({
                        name: alternative.name ?? entry.name,
                        attributes: [...entry.attributes, ...alternative.attributes],
                        inputs: [],
                    })),
            );
        }
    }
    return result;
}

function propertyNamespace(node) {
    if (node.type === 'decl' && node.isNested) return node.prop;
    if (node.type === 'rule' && node.selector.trimEnd().endsWith(':')) return node.selector.trimEnd().slice(0, -1).trim();
}

function declarationSubject(node) {
    let property = node.prop;
    for (let parent = node.parent; parent; parent = parent.parent) {
        const namespace = propertyNamespace(parent);
        if (namespace) property = `${namespace}-${property}`;
        else if (parent.type === 'rule') return { rule: parent, property };
    }
    return {};
}

/** Shared synchronous evaluator for stylesheet files and Angular inline styles. */
export function evaluateStylesheet(root, index, propertyOptions, report) {
    const declarations = new Map();
    const owners = new Map();
    const unreadable = new Set();
    const cannotVerify = (node, detail) => {
        if (unreadable.has(node)) return;
        unreadable.add(node);
        report({ message: `Cannot verify ${detail} on a design-system control. Use explicit host layout or change the package.`, node });
    };
    // Named @at-root blocks replace the subject; the nesting resolver only knows @nest.
    const stylesheet = root.clone();
    stylesheet.walkAtRules('at-root', (node) => {
        if (!node.params || node.params.startsWith('(') || node.params.includes('#{')) return;
        const selector = node.params.includes('&') ? resolveNestedSelector(node.params, node).join(', ') : node.params;
        const replacement = postcss.rule({ selector, source: node.source });
        replacement.append(node.nodes ?? []);
        stylesheet.append(replacement);
        node.remove();
    });
    const ownerOf = (subject) => index.match(subject, { caseInsensitiveAttributes: true }).find((component) => component.ownsAppearance);
    stylesheet.walkRules((cssRule) => {
        if (propertyNamespace(cssRule)) return;
        for (let parent = cssRule.parent; parent; parent = parent.parent) if (unreadable.has(parent)) return;
        const parentSubject = declarationSubject(cssRule).rule;
        let owner;
        try {
            for (const selector of resolveNestedSelector(cssRule.selector, cssRule)) {
                const interpolation = selector.indexOf('#{');
                if (interpolation !== -1) {
                    // Sass fragments look like extra tags to a CSS parser. A protected static prefix still needs a diagnostic.
                    try {
                        owner = selectorParser().astSync(selector.slice(0, interpolation)).nodes.flatMap(subjects).map(ownerOf).find(Boolean);
                    } catch {
                        // An incomplete prefix (e.g. an attribute value) cannot identify a subject by itself.
                    }
                }
                owner ??= selectorParser().astSync(selector).nodes.flatMap(subjects).map(ownerOf).find(Boolean);
                if (owner) break;
            }
        } catch {
            if (owners.has(parentSubject)) cannotVerify(cssRule, 'this selector');
            return;
        }
        if (cssRule.selector.includes('#{') && (owner || owners.has(parentSubject))) {
            cannotVerify(cssRule, 'an interpolated selector');
            return;
        }
        if (!owner) return;
        owners.set(cssRule, owner);
        cssRule.walkAtRules((node) => {
            if (declarationSubject(node).rule !== cssRule) return;
            if (['include', 'extend', 'apply'].includes(node.name)) cannotVerify(node, `@${node.name}`);
            if (node.name === 'at-root' && node.params.includes('#{')) cannotVerify(node, 'an interpolated @at-root selector');
        });
        cssRule.walkDecls((declaration) => {
            if (declaration.prop.startsWith('$')) return;
            for (let parent = declaration.parent; parent !== cssRule; parent = parent.parent) if (unreadable.has(parent)) return;
            const subject = declarationSubject(declaration);
            if (subject.rule !== cssRule) return;
            if (subject.property.includes('#{') || (subject.property.startsWith('--') && declaration.value.includes('#{'))) {
                cannotVerify(declaration, 'Sass interpolation');
            } else declarations.set(declaration, { owner, property: subject.property });
        });
    });
    if (!declarations.size) return;
    let check;
    let declaration;
    const policyRule = engine.rules['no-inline-styles'];
    policyRule.create({
        options: [propertyOptions],
        sourceCode: {
            parserServices: {
                designSystem: {
                    styleVisitors(evaluate) {
                        check = evaluate;
                        return {};
                    },
                },
            },
        },
        report(descriptor) {
            let message = descriptor.message ?? policyRule.meta.messages[descriptor.messageId];
            for (const [key, value] of Object.entries(descriptor.data ?? {})) message = message.replaceAll(`{{${key}}}`, value);
            report({ message, node: declaration, word: declaration.prop });
        },
    });
    if (!check) throw new Error('The design-system stylesheet adapter did not register its evaluator.');
    for (const [node, { owner, property }] of declarations) {
        declaration = node;
        check(
            {
                type: 'ObjectExpression',
                properties: [
                    {
                        type: 'Property',
                        computed: false,
                        key: { type: 'Literal', value: property.startsWith('--') ? property : property.toLowerCase() },
                        value: { type: 'Literal', value: node.value },
                    },
                ],
            },
            undefined,
            new Set(),
            owner.name,
        );
    }
}

/** Adapt CSS subjects/declarations to Angular metadata and upstream policy; neither grammar is duplicated. */
export function createDesignSystemStyleRule({ components, propertyOptions = {} }) {
    const getIndex = createComponentIndex(components);
    const rule = (enabled) => (root, result) => {
        if (!stylelint.utils.validateOptions(result, ruleName, { actual: enabled, possible: [true, false] }) || !enabled) return;
        evaluateStylesheet(root, getIndex(), propertyOptions, (descriptor) => stylelint.utils.report({ ...descriptor, result, ruleName }));
    };
    rule.ruleName = ruleName;
    rule.meta = { url: 'https://github.com/shadcn-ui/lint/blob/main/docs/rules/no-inline-styles.md' };
    return stylelint.createPlugin(ruleName, rule);
}
