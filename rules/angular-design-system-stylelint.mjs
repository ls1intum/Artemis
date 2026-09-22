import path from 'node:path';
import process from 'node:process';
import stylelint from 'stylelint';
import postcss from 'postcss';
import selectorParser from 'postcss-selector-parser';
import resolveNestedSelector from 'postcss-resolve-nested-selector';
import { plugin as engine } from './design-system/plugin.mjs';
import { createComponentIndex } from './angular-design-system-project.mjs';
import { compiledClasses, TailwindVerificationError } from './design-system/tailwind/client.mjs';
import { checkPrivateClasses } from './design-system-private-classes-stylelint.mjs';

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

function stylesheetMessage(owner, property, messageId) {
    if (messageId === 'customPropColor') {
        return `Custom property "${property}" on <${owner.name}> hardcodes a color. Reference an existing theme token; define a missing color in the theme instead of on the control.`;
    }
    if (messageId !== 'inlineStyle') return undefined;
    const inputs = ['size', 'variant', 'severity', 'density']
        .filter((name) => owner.inputs?.get(name)?.length)
        .map((name) => {
            const values = owner.inputs.get(name);
            return `${name} (${values.slice(0, 3).join(', ')}${values.length > 3 ? ', …' : ''})`;
        });
    const source = owner.file ? path.relative(process.cwd(), owner.file) : `the ${owner.name} component`;
    const guidance = inputs.length ? `Use public inputs ${inputs.join('; ')}; otherwise update ${source}.` : `Update ${source} for this treatment.`;
    return `Stylesheet property "${property}" overrides <${owner.name}>. ${guidance}`;
}

function resolveAtRoots(root) {
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
    return stylesheet;
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
    const stylesheet = resolveAtRoots(root);
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
            const { owner, property } = declarations.get(declaration);
            let message = descriptor.message ?? stylesheetMessage(owner, property, descriptor.messageId) ?? policyRule.meta.messages[descriptor.messageId];
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

/** Check selectors generated by @apply variants, including those authored on ordinary wrappers. */
export function evaluateAppliedClassSelectors(root, index, { theme, propertyOptions = {}, privateClassPrefix }, report) {
    const sites = new Map();
    resolveAtRoots(root).walkAtRules('apply', (node) => {
        const rule = declarationSubject(node).rule;
        if (rule) {
            try {
                const protectedHost = resolveNestedSelector(rule.selector, rule).some((selector) =>
                    selectorParser()
                        .astSync(selector)
                        .nodes.flatMap(subjects)
                        .some((subject) => index.match(subject, { caseInsensitiveAttributes: true }).some((component) => component.ownsAppearance)),
                );
                if (protectedHost) return; // The stylesheet evaluator already reports this opaque protected-host declaration.
            } catch {
                return; // Invalid/dynamic protected selectors are handled by the stylesheet evaluator.
            }
        }
        for (const token of node.params.split(/\s+/).filter((token) => token.includes(':'))) {
            if (!sites.has(token)) sites.set(token, new Set());
            sites.get(token).add(node);
        }
    });
    if (!sites.size) return;
    const tokens = [...sites.keys()];
    let styles;
    try {
        if (!theme) throw new TailwindVerificationError('No Tailwind theme is configured for @apply selector verification.');
        styles = compiledClasses(theme, tokens);
    } catch (error) {
        if (!(error instanceof TailwindVerificationError)) throw error;
        report({ message: `Cannot verify @apply selectors: ${error.message}`, node: sites.get(tokens[0]).values().next().value });
        return;
    }
    for (const [offset, css] of styles.entries()) {
        if (!css) continue;
        const generated = postcss.parse(css);
        const messages = new Set();
        const collect = ({ message }) => messages.add(message);
        if (privateClassPrefix) checkPrivateClasses(generated, privateClassPrefix, collect);
        if (!messages.size) evaluateStylesheet(generated, index, propertyOptions, collect);
        for (const message of messages)
            for (const node of sites.get(tokens[offset])) report({ message: `@apply "${tokens[offset]}" generates a forbidden selector. ${message}`, node, word: tokens[offset] });
    }
}

/** Adapt CSS subjects/declarations to Angular metadata and upstream policy; neither grammar is duplicated. */
export function createDesignSystemStyleRule({ components, propertyOptions = {}, theme, privateClassPrefix }) {
    const getIndex = createComponentIndex(components);
    const rule = (enabled) => (root, result) => {
        if (!stylelint.utils.validateOptions(result, ruleName, { actual: enabled, possible: [true, false] }) || !enabled) return;
        const index = getIndex();
        const report = (descriptor) => stylelint.utils.report({ ...descriptor, result, ruleName });
        evaluateStylesheet(root, index, propertyOptions, report);
        evaluateAppliedClassSelectors(root, index, { theme, propertyOptions, privateClassPrefix }, report);
    };
    rule.ruleName = ruleName;
    rule.meta = { url: 'https://github.com/shadcn-ui/lint/blob/main/docs/rules/no-inline-styles.md' };
    return stylelint.createPlugin(ruleName, rule);
}
