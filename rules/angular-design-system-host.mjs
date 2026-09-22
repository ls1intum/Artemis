import { dirname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { CssSelector, Lexer, Parser, ParseLocation, ParseSourceFile, ParseSourceSpan } from '@angular/compiler';
import { isClassAttribute } from './design-system/expressions.mjs';
import postcss from 'postcss';
import { createClassResolver } from './angular-design-system-owner.mjs';
import { classExpressionValues, expressionOf } from './angular-design-system-expressions.mjs';

const angularCoreDirectory = dirname(fileURLToPath(import.meta.resolve('@angular/core/package.json'))) + sep;

function keyOf(node) {
    return node?.type === 'Identifier' ? node.name : node?.type === 'Literal' ? String(node.value) : undefined;
}

export function createAngularMetadataReader(ast, services) {
    const imports = new Map();
    const namespaces = new Set();
    const constants = new Map();
    for (const statement of ast.body) {
        if (statement.type === 'ImportDeclaration' && statement.source.value === '@angular/core' && statement.importKind !== 'type') {
            for (const specifier of statement.specifiers) {
                if (specifier.type === 'ImportSpecifier') imports.set(specifier.local.name, keyOf(specifier.imported));
                if (specifier.type === 'ImportNamespaceSpecifier') namespaces.add(specifier.local.name);
            }
        }
        const declaration = statement.declaration ?? statement;
        if (declaration.type === 'VariableDeclaration' && declaration.kind === 'const') {
            for (const entry of declaration.declarations) if (entry.id.type === 'Identifier') constants.set(entry.id.name, entry.init);
        }
    }
    const checker = services?.program?.getTypeChecker();
    const angularName = (callee) => {
        // Re-exported decorators retain their Angular symbol identity in the existing typed parser project.
        if (checker) {
            const node = services.esTreeNodeToTSNodeMap?.get(callee);
            let symbol = node && checker.getSymbolAtLocation(node);
            if (symbol?.flags & ts.SymbolFlags.Alias) symbol = checker.getAliasedSymbol(symbol);
            return symbol?.declarations?.some((declaration) => declaration.getSourceFile().fileName.startsWith(angularCoreDirectory)) ? symbol.name : undefined;
        }
        if (callee?.type === 'Identifier') return imports.get(callee.name);
        if (callee?.type === 'MemberExpression' && !callee.computed && namespaces.has(callee.object.name)) return keyOf(callee.property);
    };
    const valueOf = (node, seen = new Set()) => {
        if (!node || seen.has(node)) return undefined;
        seen.add(node);
        if (['TSAsExpression', 'TSSatisfiesExpression', 'TSNonNullExpression'].includes(node.type)) return valueOf(node.expression, seen);
        if (node.type === 'Identifier') return valueOf(constants.get(node.name), seen);
        if (node.type === 'ObjectExpression') {
            const properties = node.properties.flatMap((entry) => {
                if (entry.type === 'SpreadElement') {
                    const spread = valueOf(entry.argument, new Set(seen));
                    return spread?.type === 'ObjectExpression' ? spread.properties : [entry];
                }
                if (entry.computed) {
                    const key = valueOf(entry.key, new Set(seen));
                    if (key?.type === 'Literal') return [{ ...entry, computed: false, key }];
                }
                return [entry];
            });
            return { ...node, properties };
        }
        if (node.type === 'TemplateLiteral' && !node.expressions.length) return { ...node, type: 'Literal', value: node.quasis[0].value.cooked };
        return node;
    };
    const property = (object, name) =>
        object?.properties?.findLast((entry) => entry.type === 'Property' && (entry.computed ? valueOf(entry.key)?.value : keyOf(entry.key)) === name);
    return { angularName, valueOf, property };
}

const dynamic = (at) => ({ type: 'AngularDynamicValue', loc: at.loc, range: at.range });

/** Read host expressions once, independently of appearance ownership. */
export function createAngularHostReader(context) {
    const ast = context.sourceCode.ast;
    const { angularName, valueOf, property } = createAngularMetadataReader(ast, context.sourceCode.parserServices);
    const parser = new Parser(new Lexer());
    const source = new ParseSourceFile(context.sourceCode.text, context.filename);
    function binding(text, at, resolve) {
        if (typeof text !== 'string') return dynamic(at);
        const start = new ParseLocation(source, at.range[0], at.loc.start.line - 1, at.loc.start.column);
        const end = new ParseLocation(source, at.range[1], at.loc.end.line - 1, at.loc.end.column);
        const expression = parser.parseSimpleBinding(text, new ParseSourceSpan(start, end), at.range[0]);
        return expression.errors.length ? dynamic(at) : expressionOf(expression, at, (name) => resolve(name, at));
    }
    function hosts(declaration, include = () => true) {
        const result = [];
        for (const decorator of declaration.decorators ?? []) {
            const call = decorator.expression;
            if (call.type !== 'CallExpression' || !['Component', 'Directive'].includes(angularName(call.callee))) continue;
            const metadata = valueOf(call.arguments[0]);
            const selector = valueOf(property(metadata, 'selector')?.value)?.value;
            if (!include(selector)) continue;
            // @HostBinding does not make readonly initializers mutable; other decorators still remain unknown.
            const members = declaration.body.body.map((member) =>
                member.decorators?.length && member.decorators.every((entry) => angularName(entry.expression.callee) === 'HostBinding') ? { ...member, decorators: [] } : member,
            );
            const resolve = createClassResolver(ast, { ...declaration, body: { ...declaration.body, body: members } });
            const records = [];
            if (metadata?.properties?.some((entry) => entry.type !== 'Property' || entry.computed)) records.push({ name: 'class', expression: dynamic(metadata), at: metadata });
            const hostProperty = property(metadata, 'host');
            if (hostProperty) {
                const host = valueOf(hostProperty.value);
                if (host?.type !== 'ObjectExpression') records.push({ name: 'class', expression: dynamic(hostProperty), at: hostProperty });
                else
                    for (const entry of host.properties) {
                        const name = entry.type === 'Property' && !entry.computed ? keyOf(entry.key) : undefined;
                        if (!name) records.push({ name: 'class', expression: dynamic(entry), at: entry });
                        else {
                            const raw = valueOf(entry.value)?.value;
                            const bound = name.startsWith('[') && name.endsWith(']');
                            records.push({
                                name: bound ? name.slice(1, -1) : name,
                                expression: bound
                                    ? binding(raw, entry, resolve)
                                    : raw === undefined
                                      ? dynamic(entry)
                                      : { type: 'Literal', value: raw, loc: entry.loc, range: entry.range },
                                at: entry,
                            });
                        }
                    }
            }
            for (const member of declaration.body.body) {
                for (const hostBinding of member.decorators ?? []) {
                    const expression = hostBinding.expression;
                    if (expression.type !== 'CallExpression' || angularName(expression.callee) !== 'HostBinding') continue;
                    const name = expression.arguments.length ? valueOf(expression.arguments[0])?.value : keyOf(member.key);
                    records.push({ name: typeof name === 'string' ? name : 'class', expression: resolve(keyOf(member.key), hostBinding) ?? dynamic(hostBinding), at: hostBinding });
                }
            }
            result.push({ selector, records });
        }
        return result;
    }
    return hosts;
}

/** Adapt actual Angular host metadata on protected selectors, not arbitrary application hosts. */
export function createAngularHostAdapter(context, index) {
    const readHosts = createAngularHostReader(context);
    function hosts(declaration) {
        const matchesBySelector = new Map();
        const hosts = readHosts(declaration, (selector) => {
            if (typeof selector !== 'string') return false;
            const matches = new Set();
            for (const selected of CssSelector.parse(selector)) {
                const attributes = [];
                for (let i = 0; i < selected.attrs.length; i += 2) attributes.push({ name: selected.attrs[i], value: selected.attrs[i + 1] });
                if (selected.classNames.length) attributes.push({ name: 'class', value: selected.classNames.join(' ') });
                for (const component of index.match({ name: selected.element, attributes, inputs: [] })) if (component.ownsAppearance) matches.add(component);
            }
            matchesBySelector.set(selector, matches);
            return matches.size > 0;
        });
        return hosts.map(({ selector, records }) => ({ matches: matchesBySelector.get(selector), records }));
    }
    function normalized(name) {
        const value = name.replace(/^attr\./, '');
        return ['class', 'style'].includes(value.toLowerCase()) ? value.toLowerCase() : value;
    }
    function style(record) {
        const name = normalized(record.name);
        const { at, expression } = record;
        const node = (type, values) => ({ type, ...values, loc: at.loc, range: at.range });
        const property = (name, value) => node('Property', { computed: false, kind: 'init', key: node('Literal', { value: name }), value });
        if (name.startsWith('style.')) return node('ObjectExpression', { properties: [property(name.split('.')[1], expression)] });
        if (expression.type !== 'Literal' || typeof expression.value !== 'string') return expression;
        try {
            const properties = [];
            postcss
                .parse(`a {${expression.value}}`)
                .walkDecls((entry) => properties.push(property(entry.prop.startsWith('--') ? entry.prop : entry.prop.toLowerCase(), node('Literal', { value: entry.value }))));
            return node('ObjectExpression', { properties });
        } catch {
            return dynamic(at);
        }
    }

    return {
        classSiteVisitors(_context, _options, emit) {
            return {
                ClassDeclaration(declaration) {
                    for (const { matches, records } of hosts(declaration))
                        for (const record of records) {
                            const name = normalized(record.name);
                            if (!isClassAttribute(name) && !name.startsWith('class.')) continue;
                            const strings = [];
                            const unresolved = [];
                            const collect = (value, node) => strings.push({ value, node });
                            if (name.startsWith('class.')) collect(name.slice(6), record.at);
                            else classExpressionValues(record.expression, record.at, collect, (node) => unresolved.push(node));
                            for (const component of matches)
                                emit({
                                    contextualStrings: strings,
                                    vocabularyStrings: strings,
                                    unresolved,
                                    component: component.name,
                                    componentFile: component.file,
                                    variants: component.inputs.get('variant') ?? [],
                                    sizes: component.inputs.get('size') ?? [],
                                    wrapper: null,
                                    attribute: record.name,
                                    node: record.at,
                                    enclosingContainer: () => null,
                                    closedParent: () => null,
                                });
                        }
                },
            };
        },
        styleVisitors(check) {
            return {
                ClassDeclaration(declaration) {
                    for (const { matches, records } of hosts(declaration))
                        for (const record of records) {
                            const name = normalized(record.name);
                            if (name !== 'style' && !name.startsWith('style.')) continue;
                            for (const component of matches) check(style(record), record.at, new Set(), component.name);
                        }
                },
            };
        },
        colorAttributeVisitors(check, visitors) {
            return {
                ...visitors,
                ClassDeclaration(declaration) {
                    visitors.ClassDeclaration?.(declaration);
                    for (const { records } of hosts(declaration))
                        for (const record of records) {
                            const name = normalized(record.name);
                            const attribute = { 'stop-color': 'stopColor', 'flood-color': 'floodColor', 'lighting-color': 'lightingColor' }[name] ?? name;
                            if (record.expression.type === 'Literal') check(record.at, attribute, record.expression.value);
                        }
                },
            };
        },
    };
}
