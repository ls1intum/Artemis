function kind(node) {
    return node?.type ?? node?.constructor?.name;
}

/** Translate Angular expressions, not template source text, to the upstream expression model. */
export function expressionOf(ast, at, resolve = () => undefined, seen = new Set()) {
    const node = (type, values = {}) => ({ type, loc: at.loc, range: at.range, ...values });
    if (kind(ast) === 'ASTWithSource') return expressionOf(ast.ast, at, resolve, seen);
    switch (kind(ast)) {
        case 'LiteralPrimitive':
            return node('Literal', { value: ast.value });
        case 'Interpolation':
        case 'TemplateLiteral': {
            const strings = ast.strings ?? ast.elements.map((element) => element.text);
            if (!ast.expressions.length) return node('Literal', { value: strings[0] });
            return node('TemplateLiteral', {
                quasis: strings.map((value, index) => node('TemplateElement', { value: { cooked: value, raw: value }, tail: index === strings.length - 1 })),
                expressions: ast.expressions.map((expression) => expressionOf(expression, at, resolve, seen)),
            });
        }
        case 'Conditional':
            return node('ConditionalExpression', { consequent: expressionOf(ast.trueExp, at, resolve, seen), alternate: expressionOf(ast.falseExp, at, resolve, seen) });
        case 'Binary':
            if (['&&', '||', '??'].includes(ast.operation)) {
                return node('LogicalExpression', { operator: ast.operation, left: expressionOf(ast.left, at, resolve, seen), right: expressionOf(ast.right, at, resolve, seen) });
            }
            break;
        case 'LiteralArray':
            return node('ArrayExpression', { elements: ast.expressions.map((value) => expressionOf(value, at, resolve, seen)) });
        case 'LiteralMap':
            return node('ObjectExpression', {
                properties: ast.keys.map((key, index) =>
                    key.kind === 'property'
                        ? node('Property', { key: node('Literal', { value: key.key }), value: expressionOf(ast.values[index], at, resolve, seen), computed: false, kind: 'init' })
                        : node('SpreadElement', { argument: node('AngularDynamicValue') }),
                ),
            });
        case 'PropertyRead':
            if (['ImplicitReceiver', 'ThisReceiver'].includes(kind(ast.receiver)) && !seen.has(ast.name)) {
                const value = resolve(ast.name, kind(ast.receiver) === 'ThisReceiver');
                if (value) return value;
            }
            break;
    }
    return node('AngularDynamicValue');
}

export function classValues(ast, at, emit, unreadable, resolve) {
    if (kind(ast) === 'ASTWithSource') return classValues(ast.ast, at, emit, unreadable, resolve);
    if (kind(ast) === 'Interpolation') {
        if (ast.strings.some((value, index) => (index > 0 && value && !/^\s/.test(value)) || (index < ast.strings.length - 1 && value && !/\s$/.test(value)))) {
            unreadable(at);
        } else {
            ast.strings.forEach((value) => emit(value, at));
            ast.expressions.forEach((value) => classValues(value, at, emit, unreadable, resolve));
        }
        return;
    }
    classExpressionValues(expressionOf(ast, at, resolve), at, emit, unreadable);
}

export function classExpressionValues(expression, at, emit, unreadable) {
    const visit = (expression) => {
        switch (expression.type) {
            case 'Literal':
                if (typeof expression.value === 'string') emit(expression.value, at);
                else if (expression.value !== false && expression.value != null) unreadable(at);
                break;
            case 'ConditionalExpression':
                visit(expression.consequent);
                visit(expression.alternate);
                break;
            case 'LogicalExpression':
                if (expression.operator !== '&&') visit(expression.left);
                visit(expression.right);
                break;
            case 'ArrayExpression':
                expression.elements.forEach(visit);
                break;
            case 'ObjectExpression':
                expression.properties.forEach((property) => (property.type === 'Property' ? emit(String(property.key.value), at) : unreadable(at)));
                break;
            default:
                unreadable(at);
        }
    };
    visit(expression);
}
