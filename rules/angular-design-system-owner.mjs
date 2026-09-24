import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { parse } from '@typescript-eslint/parser';

function walk(node, visit, parent) {
    if (!node || typeof node !== 'object') return;
    if (typeof node.type === 'string') visit(node, parent);
    for (const [key, value] of Object.entries(node)) {
        if (key === 'loc' || key === 'range' || key === 'parent') continue;
        if (Array.isArray(value)) value.forEach((child) => walk(child, visit, node));
        else if (value && typeof value === 'object') walk(value, visit, node);
    }
}

function unwrap(node) {
    while (['TSAsExpression', 'TSSatisfiesExpression', 'TSNonNullExpression', 'TSTypeAssertion'].includes(node?.type)) node = node.expression;
    return node;
}

function propertyName(node) {
    return node?.type === 'Identifier' ? node.name : node?.type === 'Literal' ? node.value : undefined;
}

function declarations(ast) {
    return ast.body.map((node) => node.declaration ?? node);
}

function constantDeclarations(ast) {
    const constants = new Map();
    for (const node of declarations(ast)) {
        if (node.type !== 'VariableDeclaration' || node.kind !== 'const') continue;
        for (const declaration of node.declarations) {
            if (declaration.id.type === 'Identifier') constants.set(declaration.id.name, declaration);
        }
    }
    return constants;
}

function classOwner(ast, component) {
    const constants = constantDeclarations(ast);
    const constantReferences = new Map([...constants.keys()].map((name) => [name, []]));
    walk(ast, (node) => {
        if (node.type === 'Identifier' && constantReferences.has(node.name)) constantReferences.get(node.name).push({ range: node.range });
    });
    const fields = component.body.body.filter((node) => node.type === 'PropertyDefinition');
    const memberReferences = [];
    const escapedThis = [];
    walk(component, (node, parent) => {
        if (node.type === 'ThisExpression' && (parent?.type !== 'MemberExpression' || parent.object !== node)) escapedThis.push({ range: node.range });
        if (node.type === 'MemberExpression' && node.object.type === 'ThisExpression')
            memberReferences.push({
                name: node.computed && node.property.type !== 'Literal' ? undefined : propertyName(node.property),
                range: node.range,
            });
    });

    return { fields, memberReferences, escapedThis, constants, constantReferences };
}

export function createClassResolver(ast, component) {
    const owner = classOwner(ast, component);
    return (name, reportNode) => resolveField(owner, name, reportNode);
}

function inspect(filename) {
    let ast;
    try {
        const source = readFileSync(filename, 'utf8');
        if (!source.includes('@angular/core') || !source.includes('Component')) return [];
        ast = parse(source, { loc: true, range: true });
    } catch {
        return [];
    }
    const names = new Set();
    const namespaces = new Set();
    for (const node of ast.body) {
        if (node.type !== 'ImportDeclaration' || node.source.value !== '@angular/core' || node.importKind === 'type') continue;
        for (const specifier of node.specifiers) {
            if (specifier.type === 'ImportSpecifier' && propertyName(specifier.imported) === 'Component') names.add(specifier.local.name);
            if (specifier.type === 'ImportNamespaceSpecifier') namespaces.add(specifier.local.name);
        }
    }
    const constants = constantDeclarations(ast);
    const owners = [];
    for (const component of declarations(ast)) {
        if (component.type !== 'ClassDeclaration') continue;
        for (const decorator of component.decorators ?? []) {
            const call = decorator.expression;
            if (call.type !== 'CallExpression') continue;
            const callee = call.callee;
            const angularComponent =
                (callee.type === 'Identifier' && names.has(callee.name)) ||
                (callee.type === 'MemberExpression' &&
                    !callee.computed &&
                    callee.object.type === 'Identifier' &&
                    namespaces.has(callee.object.name) &&
                    callee.property.name === 'Component');
            if (!angularComponent || call.arguments[0]?.type !== 'ObjectExpression') continue;
            const metadata = call.arguments[0];
            // A spread/computed property can replace the template metadata at runtime.
            if (metadata.properties.some((entry) => entry.type !== 'Property' || entry.computed)) continue;
            const values = new Map(metadata.properties.map((entry) => [propertyName(entry.key), entry.value]));
            let template = unwrap(values.get('templateUrl'));
            if (template?.type === 'Identifier') template = unwrap(constants.get(template.name)?.init);
            if (template?.type === 'TemplateLiteral' && !template.expressions.length) template = { type: 'Literal', value: template.quasis[0].value.cooked };
            const templateUrl = template?.type === 'Literal' && typeof template.value === 'string' ? resolve(dirname(filename), template.value) : undefined;
            const inline = values.has('template') && !values.has('templateUrl');
            if (templateUrl || inline) {
                owners.push({ ...classOwner(ast, component), filename, templateUrl, inline });
            }
        }
    }
    return owners;
}

function* sourceFiles(root) {
    let entries;
    try {
        entries = readdirSync(root, { withFileTypes: true });
    } catch {
        return;
    }
    for (const entry of entries) {
        const filename = resolve(root, entry.name);
        if (entry.isDirectory() && !['node_modules', '.git'].includes(entry.name)) yield* sourceFiles(filename);
        else if (entry.isFile() && entry.name.endsWith('.ts') && !/\.(?:d|spec|stories)\.ts$/.test(entry.name)) yield filename;
    }
}

function contains(parent, node) {
    return node.range[0] >= parent.range[0] && node.range[1] <= parent.range[1];
}

function resolveField(owner, name, reportNode) {
    const field = owner.fields.find((node) => !node.computed && propertyName(node.key) === name);
    if (!field?.readonly || field.static || field.decorators.length || !field.value) return undefined;
    // readonly is shallow; an escaping instance or referenced field can be mutated.
    const unsafe =
        owner.escapedThis.some((reference) => !contains(field, reference)) ||
        owner.memberReferences.some((reference) => (reference.name === undefined || reference.name === name) && !contains(field, reference));
    if (unsafe) return undefined;
    const location = { loc: reportNode.loc, range: reportNode.range };
    const unknown = () => ({ type: 'AngularDynamicValue', ...location });
    function convert(expression, allowConstant = true) {
        const node = unwrap(expression);
        if (!node) return undefined;
        switch (node.type) {
            case 'Literal':
                return { type: 'Literal', value: node.value, raw: node.raw, ...location };
            case 'TemplateLiteral':
                return node.expressions.length ? undefined : { type: 'Literal', value: node.quasis[0].value.cooked, ...location };
            case 'Identifier': {
                const constant = allowConstant && owner.constants.get(node.name);
                if (!constant?.init) return undefined;
                const escaped = owner.constantReferences.get(node.name).some((reference) => !contains(constant, reference) && !contains(field, reference));
                return escaped ? undefined : convert(constant.init, false);
            }
            case 'ArrayExpression': {
                const elements = node.elements.map((entry) => convert(entry, allowConstant));
                return elements.every(Boolean) ? { type: node.type, elements, ...location } : undefined;
            }
            case 'ObjectExpression': {
                const properties = [];
                for (const entry of node.properties) {
                    if (entry.type !== 'Property' || entry.computed || entry.method || entry.kind !== 'init') return undefined;
                    const key = { type: 'Literal', value: String(propertyName(entry.key)), ...location };
                    properties.push({
                        type: 'Property',
                        kind: 'init',
                        computed: false,
                        method: false,
                        shorthand: false,
                        key,
                        value: convert(entry.value, allowConstant) ?? unknown(),
                        ...location,
                    });
                }
                return { type: node.type, properties, ...location };
            }
            case 'ConditionalExpression': {
                const consequent = convert(node.consequent, allowConstant);
                const alternate = convert(node.alternate, allowConstant);
                return consequent && alternate ? { type: node.type, test: unknown(), consequent, alternate, ...location } : undefined;
            }
            case 'LogicalExpression': {
                const right = convert(node.right, allowConstant);
                const left = convert(node.left, allowConstant) ?? (node.operator === '&&' ? unknown() : undefined);
                return left && right ? { type: node.type, operator: node.operator, left, right, ...location } : undefined;
            }
            default:
                return undefined;
        }
    }
    return convert(field.value);
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

/** Resolve only statically owned, same-file class values; imports, calls and inherited members stay unknown. */
export function createTemplateResolver(sourceRoots) {
    const files = new Map();
    const roots = sourceRoots.map((root) => resolve(root));
    function owners() {
        const current = new Set(roots.flatMap((root) => [...sourceFiles(root)]));
        for (const filename of files.keys()) {
            if (!current.has(filename)) files.delete(filename);
        }
        for (const filename of current) {
            let stamp;
            try {
                const stat = statSync(filename);
                stamp = `${stat.mtimeMs}:${stat.ctimeMs}:${stat.size}`;
            } catch {
                files.delete(filename);
                continue;
            }
            if (files.get(filename)?.stamp !== stamp) files.set(filename, { stamp, owners: inspect(filename) });
        }
        return [...files.values()].flatMap((file) => file.owners);
    }
    return (context) => {
        let matches;
        const locals = context.sourceCode ? templateLocals(context.sourceCode) : new Set();
        return (name, reportNode, explicitThis = false) => {
            // Template-wide reservation is conservative; explicit this.name is unambiguous.
            if (!explicitThis && locals.has(name)) return undefined;
            if (!matches) {
                const physical = resolve(context.physicalFilename ?? context.getPhysicalFilename?.() ?? context.filename ?? context.getFilename());
                matches = owners().filter((owner) => owner.templateUrl === physical || (owner.inline && owner.filename === physical));
            }
            // Shared external templates and files with multiple inline components have no unique owner.
            return matches.length === 1 ? resolveField(matches[0], name, reportNode) : undefined;
        };
    };
}
