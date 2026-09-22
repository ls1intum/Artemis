import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
import { CssSelector, SelectorMatcher } from '@angular/compiler';

function propertyName(node) {
    return node && (ts.isIdentifier(node) || ts.isStringLiteralLike(node) || ts.isNumericLiteral(node)) ? node.text : undefined;
}

function property(object, name) {
    return object?.properties?.findLast((entry) => propertyName(entry.name) === name)?.initializer;
}

function literal(node) {
    return node && (ts.isStringLiteralLike(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : undefined;
}

/** Angular supplies selector parsing; TypeScript supplies decorator and input metadata. */
export function createComponentIndex(roots) {
    let checkedAt = 0;
    let signature;
    let index;
    return () => {
        if (index && Date.now() - checkedAt < 1000) return index;
        checkedAt = Date.now();
        const files = roots.flatMap((root) => ts.sys.readDirectory(root, ['.ts'], ['**/*.spec.ts', '**/*.stories.ts', '**/node_modules/**'])).sort();
        const current = files.map((file) => `${file}:${fs.statSync(file).mtimeMs}`).join('|');
        if (index && signature === current) return index;
        const program = ts.createProgram(files, {
            target: ts.ScriptTarget.ESNext,
            module: ts.ModuleKind.Preserve,
            moduleResolution: ts.ModuleResolutionKind.Bundler,
            skipLibCheck: true,
        });
        const checker = program.getTypeChecker();
        const matcher = new SelectorMatcher();
        const htmlMatcher = new SelectorMatcher();
        const htmlAttributeNames = (selector) => {
            for (let offset = 0; offset < selector.attrs.length; offset += 2) selector.attrs[offset] = selector.attrs[offset].toLowerCase();
            selector.notSelectors.forEach(htmlAttributeNames);
            return selector;
        };
        const components = [];
        const angularSymbols = new Map();
        for (const source of program.getSourceFiles()) {
            if (source.isDeclarationFile) continue;
            for (const statement of source.statements) {
                if ((!ts.isImportDeclaration(statement) && !ts.isExportDeclaration(statement)) || statement.moduleSpecifier?.text !== '@angular/core') continue;
                const module = checker.getSymbolAtLocation(statement.moduleSpecifier);
                if (module)
                    for (const symbol of checker.getExportsOfModule(module)) {
                        const target = symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol;
                        angularSymbols.set(target, symbol.name);
                    }
            }
        }
        const importsByFile = new Map();
        const angularName = (expression) => {
            const source = expression.getSourceFile();
            let imports = importsByFile.get(source);
            if (!imports) {
                const names = new Map();
                const namespaces = new Set();
                for (const statement of source.statements) {
                    if (!ts.isImportDeclaration(statement) || statement.moduleSpecifier.text !== '@angular/core' || statement.importClause?.isTypeOnly) continue;
                    const bindings = statement.importClause?.namedBindings;
                    if (bindings && ts.isNamespaceImport(bindings)) namespaces.add(bindings.name.text);
                    if (bindings && ts.isNamedImports(bindings)) {
                        for (const binding of bindings.elements) if (!binding.isTypeOnly) names.set(binding.name.text, binding.propertyName?.text ?? binding.name.text);
                    }
                }
                imports = { names, namespaces };
                importsByFile.set(source, imports);
            }
            if (ts.isIdentifier(expression) && imports.names.has(expression.text)) return imports.names.get(expression.text);
            if (ts.isPropertyAccessExpression(expression)) {
                if (ts.isIdentifier(expression.expression) && imports.namespaces.has(expression.expression.text)) return expression.name.text;
                if (expression.name.text === 'required') return angularName(expression.expression);
            }
            let symbol = checker.getSymbolAtLocation(expression);
            if (symbol?.flags & ts.SymbolFlags.Alias) symbol = checker.getAliasedSymbol(symbol);
            return angularSymbols.get(symbol);
        };
        const stringValues = (type) => (type?.isUnion() ? type.types : type ? [type] : []).filter((entry) => entry.isStringLiteral()).map((entry) => entry.value);
        for (const file of files) {
            const source = program.getSourceFile(file);
            const staticValue = (node, seen = new Set()) => {
                if (!node || seen.has(node)) return undefined;
                seen.add(node);
                if (ts.isIdentifier(node) || ts.isPropertyAccessExpression(node)) {
                    let symbol = checker.getSymbolAtLocation(ts.isPropertyAccessExpression(node) ? node.name : node);
                    if (symbol?.flags & ts.SymbolFlags.Alias) symbol = checker.getAliasedSymbol(symbol);
                    const declaration = symbol?.valueDeclaration;
                    if (declaration && ts.isPropertyAssignment(declaration)) return staticValue(declaration.initializer, seen);
                    if (declaration && ts.isVariableDeclaration(declaration) && declaration.parent.flags & ts.NodeFlags.Const) return staticValue(declaration.initializer, seen);
                }
                if (ts.isAsExpression(node) || ts.isSatisfiesExpression(node) || ts.isParenthesizedExpression(node)) return staticValue(node.expression, seen);
                if (ts.isObjectLiteralExpression(node)) {
                    const properties = node.properties.flatMap((entry) => {
                        if (!ts.isSpreadAssignment(entry)) {
                            if (entry.name && ts.isComputedPropertyName(entry.name)) {
                                const name = literal(staticValue(entry.name.expression, new Set(seen)));
                                if (name === undefined) throw new Error(`Cannot statically read Angular metadata key in ${file}.`);
                                return [{ ...entry, name: ts.factory.createStringLiteral(name) }];
                            }
                            return [entry];
                        }
                        const spread = staticValue(entry.expression, new Set(seen));
                        if (!spread?.properties) throw new Error(`Cannot statically read Angular metadata spread in ${file}.`);
                        return [...spread.properties];
                    });
                    return { properties };
                }
                return node;
            };
            for (const declaration of source.statements) {
                if (!ts.isClassDeclaration(declaration)) continue;
                for (const decorator of ts.getDecorators(declaration) ?? []) {
                    const call = decorator.expression;
                    if (!ts.isCallExpression(call)) continue;
                    const kind = angularName(call.expression);
                    if (kind !== 'Component' && kind !== 'Directive') continue;
                    const metadata = staticValue(call.arguments[0]);
                    const selectorNode = property(metadata, 'selector');
                    if (kind === 'Directive' && !selectorNode && (!metadata || metadata.properties)) continue;
                    const selector = literal(staticValue(selectorNode));
                    if (!selector) throw new Error(`Cannot statically read Angular selector in ${file}. Design-system discovery must not silently skip declarations.`);
                    const appearanceBinding = (name) => /^(?:class|style|\[(?:class|style)(?:\.[^\]]+)?\]|\[attr\.(?:class|style)\])$/.test(name ?? '');
                    const classType = checker.getTypeAtLocation(declaration);
                    const members = checker.getPropertiesOfType(classType);
                    const hasHostAppearance = (type, seen = new Set()) => {
                        if (seen.has(type)) return false;
                        seen.add(type);
                        for (const node of type.symbol?.declarations ?? []) {
                            if (!ts.isClassDeclaration(node)) continue;
                            for (const decorator of ts.getDecorators(node) ?? []) {
                                const call = decorator.expression;
                                if (!ts.isCallExpression(call) || !['Component', 'Directive'].includes(angularName(call.expression))) continue;
                                const inheritedMetadata = staticValue(call.arguments[0]);
                                const host = staticValue(property(inheritedMetadata, 'host'));
                                if (host?.properties?.some((entry) => appearanceBinding(propertyName(entry.name)))) return true;
                                const composed = staticValue(property(inheritedMetadata, 'hostDirectives'));
                                if (composed && ts.isArrayLiteralExpression(composed))
                                    for (const entry of composed.elements) {
                                        const definition = staticValue(entry);
                                        const target = definition?.properties ? property(definition, 'directive') : entry;
                                        let symbol = target && checker.getSymbolAtLocation(target);
                                        if (symbol?.flags & ts.SymbolFlags.Alias) symbol = checker.getAliasedSymbol(symbol);
                                        if (symbol && hasHostAppearance(checker.getDeclaredTypeOfSymbol(symbol), seen)) return true;
                                    }
                            }
                        }
                        return (type.getBaseTypes?.() ?? []).some((base) => hasHostAppearance(base, seen));
                    };
                    let ownsAppearance = kind === 'Component' || hasHostAppearance(classType);
                    const inputs = new Map();
                    for (const symbol of members) {
                        const member = symbol.valueDeclaration;
                        if (!member) continue;
                        const memberType = checker.getTypeOfSymbolAtLocation(symbol, declaration);
                        for (const decorator of ts.getDecorators(member) ?? []) {
                            const call = decorator.expression;
                            if (!ts.isCallExpression(call)) continue;
                            const decoratorName = angularName(call.expression);
                            if (decoratorName === 'HostBinding') {
                                const name = literal(staticValue(call.arguments[0])) ?? symbol.name;
                                if (appearanceBinding(name) || name.startsWith('class.') || name.startsWith('style.')) ownsAppearance = true;
                            }
                            if (decoratorName === 'Input') {
                                const options = staticValue(call.arguments[0]);
                                const name = literal(options) ?? literal(staticValue(property(options, 'alias'))) ?? symbol.name;
                                inputs.set(name, stringValues(memberType));
                            }
                        }
                        const init = member.initializer;
                        if (!init || !ts.isCallExpression(init) || !['input', 'model'].includes(angularName(init.expression))) continue;
                        const name = literal(staticValue(property(staticValue(init.arguments.find(ts.isObjectLiteralExpression)), 'alias'))) ?? symbol.name;
                        const typeArgument = init.typeArguments?.[0];
                        const type =
                            memberType.typeArguments?.[0] ??
                            (typeArgument ? checker.getTypeFromTypeNode(typeArgument) : init.arguments[0] ? checker.getTypeAtLocation(init.arguments[0]) : undefined);
                        inputs.set(name, stringValues(type));
                    }
                    const declaredInputs = staticValue(property(metadata, 'inputs'));
                    if (declaredInputs && ts.isArrayLiteralExpression(declaredInputs)) {
                        for (const entry of declaredInputs.elements) {
                            const definition = literal(staticValue(entry));
                            if (!definition) continue;
                            const [name, alias = name] = definition.split(':').map((part) => part.trim());
                            const member = members.find((symbol) => symbol.name === name);
                            inputs.set(alias, member ? stringValues(checker.getTypeOfSymbolAtLocation(member, declaration)) : []);
                        }
                    }
                    const component = { name: declaration.name?.text ?? selector, file, selector, inputs, ownsAppearance };
                    matcher.addSelectables(CssSelector.parse(selector), component);
                    htmlMatcher.addSelectables(CssSelector.parse(selector).map(htmlAttributeNames), component);
                    components.push(component);
                }
            }
        }
        if (!components.length) throw new Error(`No Angular components found in ${roots.join(', ')}.`);
        signature = current;
        index = {
            components,
            match(element, { caseInsensitiveAttributes = false } = {}) {
                // HTML CSS attribute names are case-insensitive; Angular template bindings are not.
                const attributeName = (name) => (caseInsensitiveAttributes ? name.toLowerCase() : name);
                const attributes = [...element.attributes, ...element.inputs].map((attribute) => ({ ...attribute, name: attributeName(attribute.name) }));
                const names = new Set([element.name ?? '*']);
                if (!element.name || element.name === '*') {
                    // Partial CSS/host selectors can constrain a native directive without naming its element.
                    for (const component of components)
                        for (const candidate of CssSelector.parse(component.selector)) {
                            const identified = attributes.some((attribute) =>
                                attribute.name === 'class'
                                    ? typeof attribute.value === 'string' && attribute.value.split(/\s+/).some((name) => candidate.classNames.includes(name))
                                    : candidate.attrs.some((name, offset) => offset % 2 === 0 && attributeName(name) === attribute.name),
                            );
                            if (identified && candidate.element) names.add(candidate.element);
                        }
                }
                const matches = new Set();
                for (const name of names) {
                    const selector = new CssSelector();
                    selector.setElement(name);
                    for (const attribute of attributes) {
                        selector.addAttribute(attribute.name, typeof attribute.value === 'string' ? attribute.value : '');
                        if (attribute.name === 'class' && typeof attribute.value === 'string') attribute.value.split(/\s+/).forEach((name) => selector.addClassName(name));
                    }
                    (caseInsensitiveAttributes ? htmlMatcher : matcher).match(selector, (_, component) => matches.add(component));
                }
                return [...matches];
            },
        };
        return index;
    };
}

export function resolveRoots(root, directories) {
    return directories.map((directory) => path.resolve(root, directory));
}
