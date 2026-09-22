import path from 'node:path';
import process from 'node:process';
import postcss from 'postcss';
import { HtmlParser, splitNsName } from '@angular/compiler';
import { plugin as engine } from './design-system/plugin.mjs';
import { registerProject } from './design-system/project/configuration.mjs';
import { isClassAttribute } from './design-system/expressions.mjs';
import { createComponentIndex, resolveRoots } from './angular-design-system-project.mjs';
import { requireLintableTemplates } from './angular-design-system-lintable-templates.mjs';
import { createInlineStylesRule } from './angular-design-system-inline-styles.mjs';
import { createAngularHostAdapter } from './angular-design-system-host.mjs';
import { createTemplateResolver } from './angular-design-system-owner.mjs';
import { classValues, expressionOf } from './angular-design-system-expressions.mjs';

function reportNode(node) {
    if (node.loc) return node;
    const span = node.sourceSpan;
    return {
        ...node,
        loc: { start: { line: span.start.line + 1, column: span.start.col }, end: { line: span.end.line + 1, column: span.end.col } },
        range: [span.start.offset, span.end.offset],
    };
}

// HTML class/style attributes are case-insensitive; Angular input names such as ngClass are not.
function attributeName(attribute) {
    const lower = attribute.name.toLowerCase();
    return ['class', 'style'].includes(lower) ? lower : attribute.name;
}

function templateLocals(sourceCode) {
    const locals = new Set();
    const visit = (node) => {
        if (!node) return;
        if (node.type === 'LetDeclaration') locals.add(node.name);
        for (const local of [...(node.references ?? []), ...(node.variables ?? []), ...(node.contextVariables ?? [])]) locals.add(local.name);
        if (node.item) locals.add(node.item.name);
        if (node.expressionAlias) locals.add(node.expressionAlias.name);
        for (const key of sourceCode.visitorKeys[node.type] ?? []) {
            const value = node[key];
            if (Array.isArray(value)) value.forEach(visit);
            else visit(value);
        }
    };
    visit(sourceCode.ast);
    return locals;
}

/** A syntax adapter: the six adapted policies consume Angular source sites. */
export function createAngularDesignSystemPlugin({ root = process.cwd(), components, sources = components, theme, scope = 'all' }) {
    root = path.resolve(root);
    registerProject(root, path.resolve(root, theme));
    const getIndex = createComponentIndex(resolveRoots(root, components));
    const resolveTemplate = createTemplateResolver(resolveRoots(root, sources));
    const adapterFor = (context) => {
        if (!context.sourceCode.ast.templateNodes) return createAngularHostAdapter(context, getIndex());
        const resolveOwner = resolveTemplate(context);
        const locals = templateLocals(context.sourceCode);
        // Conservatively reserve locals across the template; explicit this.name is unambiguous.
        const resolve = (name, at, explicitThis) => (explicitThis || !locals.has(name) ? resolveOwner(name, at) : undefined);
        const index = getIndex();
        const componentOf = (element) => index.match(element).find((entry) => entry.ownsAppearance);
        const styleExpression = (attribute) => {
            const at = reportNode(attribute);
            const property = (name, value) => ({ type: 'Property', key: { type: 'Literal', value: name }, value, computed: false, kind: 'init', loc: at.loc, range: at.range });
            if (attribute.keySpan?.details?.startsWith('style.')) {
                return {
                    type: 'ObjectExpression',
                    properties: [
                        property(
                            attribute.name,
                            expressionOf(attribute.value, at, (name, explicitThis) => resolve(name, at, explicitThis)),
                        ),
                    ],
                    loc: at.loc,
                    range: at.range,
                };
            }
            const bound = attribute.type === 'TextAttribute' ? undefined : expressionOf(attribute.value, at, (name, explicitThis) => resolve(name, at, explicitThis));
            if (bound && !(bound.type === 'Literal' && typeof bound.value === 'string')) {
                if (attribute.name === 'ngStyle' && bound.type === 'ObjectExpression') {
                    for (const entry of bound.properties) if (entry.type === 'Property') entry.key.value = String(entry.key.value).split('.')[0];
                }
                return bound;
            }
            try {
                const properties = [];
                postcss.parse(`a {${bound?.value ?? attribute.value}}`).walkDecls((declaration) =>
                    properties.push(
                        property(declaration.prop.startsWith('--') ? declaration.prop : declaration.prop.toLowerCase(), {
                            type: 'Literal',
                            value: declaration.value,
                            loc: at.loc,
                            range: at.range,
                        }),
                    ),
                );
                return { type: 'ObjectExpression', properties, loc: at.loc, range: at.range };
            } catch {
                return { type: 'AngularDynamicValue', loc: at.loc, range: at.range };
            }
        };
        return {
            classSiteVisitors(_context, _options, emit) {
                return {
                    Element(element) {
                        const matches = index.match(element).filter((entry) => entry.ownsAppearance);
                        if (scope === 'components' && !matches.length) return;
                        const parents = context.sourceCode
                            .getAncestors(element)
                            .filter((node) => node.type === 'Element')
                            .reverse();
                        const parentName = (parent) => componentOf(parent)?.name ?? parent.name;
                        for (const attribute of [...element.attributes, ...element.inputs]) {
                            const key = attribute.keySpan?.details ?? '';
                            if (!isClassAttribute(attributeName(attribute)) && !key.startsWith('class.')) continue;
                            const at = reportNode(attribute);
                            const strings = [];
                            const unresolved = [];
                            const collect = (value, node) => strings.push({ value, node });
                            if (key.startsWith('class.')) collect(attribute.name, at);
                            else if (attribute.type === 'TextAttribute') collect(attribute.value, at);
                            else
                                classValues(
                                    attribute.value,
                                    at,
                                    collect,
                                    (node) => unresolved.push(node),
                                    (name, explicitThis) => resolve(name, at, explicitThis),
                                );
                            for (const component of matches.length ? matches : [undefined]) {
                                emit({
                                    contextualStrings: strings,
                                    vocabularyStrings: strings,
                                    unresolved,
                                    component: component?.name ?? null,
                                    componentFile: component?.file ?? null,
                                    variants: component?.inputs.get('variant') ?? [],
                                    sizes: component?.inputs.get('size') ?? [],
                                    attribute: attribute.name,
                                    node: at,
                                    enclosingContainer: (accepts) => {
                                        const parentIndex = parents.findIndex((parent) => accepts(parentName(parent)));
                                        return parentIndex < 0 ? null : { name: parentName(parents[parentIndex]), direct: parentIndex === 0 };
                                    },
                                    closedParent: (accepts) => (parents[0] && !accepts(parentName(parents[0])) ? parentName(parents[0]) : null),
                                });
                            }
                        }
                    },
                };
            },
            styleVisitors(check, reportStyleElement) {
                return {
                    Program() {
                        // Angular's template AST omits styles because the compiler extracts them.
                        const visit = (nodes) => {
                            for (const node of nodes) {
                                if (node.name && splitNsName(node.name)[1].toLowerCase() === 'style') reportStyleElement(reportNode(node));
                                if (node.children) visit(node.children);
                            }
                        };
                        visit(new HtmlParser().parse(context.sourceCode.text, context.filename).rootNodes);
                    },
                    Element(element) {
                        if (scope === 'components' && !componentOf(element)) return;
                        for (const attribute of [...element.attributes, ...element.inputs]) {
                            if (!['style', 'ngStyle'].includes(attributeName(attribute)) && !attribute.keySpan?.details?.startsWith('style.')) continue;
                            check(styleExpression(attribute), reportNode(attribute), new Set(), componentOf(element)?.name ?? '');
                        }
                    },
                };
            },
            colorAttributeVisitors(check, visitors) {
                return {
                    ...visitors,
                    Element(element) {
                        visitors.Element?.(element);
                        if (componentOf(element) || scope === 'components') return;
                        for (const attribute of [...element.attributes, ...element.inputs]) {
                            const value = attribute.type === 'TextAttribute' ? attribute.value : expressionOf(attribute.value, reportNode(attribute)).value;
                            const name = { 'stop-color': 'stopColor', 'flood-color': 'floodColor', 'lighting-color': 'lightingColor' }[attribute.name] ?? attribute.name;
                            check(reportNode(attribute), name, value);
                        }
                    },
                };
            },
        };
    };
    const rules = Object.fromEntries(
        Object.entries(engine.rules).map(([name, rule]) => [
            name,
            {
                ...rule,
                create(context) {
                    const adapter = adapterFor(context);
                    const sourceCode = new Proxy(
                        {},
                        {
                            get(_, key) {
                                const target = context.sourceCode;
                                if (key === 'parserServices') return { ...target.parserServices, designSystem: adapter };
                                const value = Reflect.get(target, key);
                                return typeof value === 'function' ? value.bind(target) : value;
                            },
                        },
                    );
                    const adapted = Object.create(context);
                    Object.defineProperty(adapted, 'sourceCode', { value: sourceCode });
                    return rule.create(adapted);
                },
            },
        ]),
    );
    rules['no-restyle-stylesheets'] = createInlineStylesRule(getIndex);
    rules['require-lintable-templates'] = requireLintableTemplates;
    return { meta: { name: 'angular-design-system' }, rules };
}
