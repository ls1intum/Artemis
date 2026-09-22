import postcss from 'postcss';

/**
 * Consumer-side checks, not a Tailwind grammar or a CSS cascade analyser. Angular owns parsing;
 * PostCSS owns declaration parsing. Package implementations are excluded by the ESLint config.
 * Inspired by https://github.com/shadcn-ui/lint (no-restyle, require-static-classes, no-inline-styles).
 */
const APPEARANCE_UTILITY =
    /^(?:p(?:[xytrblse]|b[se])?|padding|space|bg|text|font|leading|tracking|rounded|border|divide|ring|outline|shadow|inset-shadow|drop-shadow|blur|brightness|contrast|saturate|grayscale|invert|sepia|backdrop|mask|appearance|fill|stroke|decoration|accent|caret|placeholder|from|via|to|opacity|indent|hyphens|line-clamp|list|underline-offset)(?:-|$)|^(?:italic|not-italic|antialiased|subpixel-antialiased|uppercase|lowercase|capitalize|normal-case|underline|overline|line-through|no-underline|ordinal|slashed-zero|lining-nums|oldstyle-nums|proportional-nums|tabular-nums|diagonal-fractions|stacked-fractions|normal-nums)$/;
const GUIDANCE = {
    tumUiButton: 'Use size, variant, severity or rounded.',
    'tum-ui-button': 'Use size, variant, severity or rounded.',
    tumUiInput: 'Use tumUiInputSize and tumUiInputInvalid.',
    tumUiTextarea: 'Use tumUiInputSize and tumUiInputInvalid.',
    'tum-ui-panel': 'Use density and showHeader for panel presentation.',
    'tum-ui-tag': 'Use severity or rounded.',
    'tum-ui-message': 'Use severity.',
};
const TEXT_LAYOUT = /^text-(?:left|center|right|justify|start|end|wrap|nowrap|balance|pretty|ellipsis|clip)$/;
const LAYOUT_PROPERTY =
    /^(?:(?:min-|max-)?(?:width|height|inline-size|block-size)|margin(?:-.+)?|inset(?:-.+)?|top|right|bottom|left|display|position|z-index|overflow(?:-.+)?|flex(?:-.+)?|grid(?:-.+)?|gap|row-gap|column-gap|align-(?:self|items|content)|justify-(?:self|items|content)|order|float|clear|vertical-align|pointer-events|cursor|white-space|text-align|resize)$/;

function isAllowedProperty(property) {
    return LAYOUT_PROPERTY.test(property) || property.startsWith('--tumaet-ui-');
}

function cssProperty(name) {
    return name.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
}

// Split variants without confusing colons inside arbitrary values/selectors with variant separators.
function utilityOf(token) {
    let depth = 0;
    let start = 0;
    for (let index = 0; index < token.length; index++) {
        if (token[index] === '\\') {
            index++;
        } else if (token[index] === '[' || token[index] === '(') {
            depth++;
        } else if (token[index] === ']' || token[index] === ')') {
            depth--;
        } else if (token[index] === ':' && depth === 0) {
            start = index + 1;
        }
    }
    return token.slice(start).replace(/^!|!$/g, '').replace(/^-/, '');
}

/**
 * Read only complete class alternatives. Do not read the condition as a class, execute signals,
 * or guess what an imported value/function returns. Unknown values are reported, not silently trusted.
 */
function classesIn(ast, emit, unreadable) {
    switch (ast?.type) {
        case 'ASTWithSource':
            classesIn(ast.ast, emit, unreadable);
            break;
        case 'LiteralPrimitive':
            if (typeof ast.value === 'string') emit(ast.value);
            else if (ast.value !== null && ast.value !== undefined && ast.value !== false) unreadable();
            break;
        case 'Conditional':
            classesIn(ast.trueExp, emit, unreadable);
            classesIn(ast.falseExp, emit, unreadable);
            break;
        case 'LiteralArray':
            ast.expressions.forEach((value) => classesIn(value, emit, unreadable));
            break;
        case 'LiteralMap':
            ast.keys.forEach((key) => (key.kind === 'property' ? emit(key.key) : unreadable()));
            break;
        case 'Binary':
            if (ast.operation === '&&') {
                classesIn(ast.right, emit, unreadable);
            } else if (ast.operation === '||' || ast.operation === '??') {
                classesIn(ast.left, emit, unreadable);
                classesIn(ast.right, emit, unreadable);
            } else {
                unreadable();
            }
            break;
        case 'Interpolation':
            // Each interpolation must sit at a class boundary, not build "bg-{{color}}".
            if (ast.strings.some((value, index) => (index > 0 && value && !/^\s/.test(value)) || (index < ast.strings.length - 1 && value && !/\s$/.test(value)))) {
                unreadable();
            } else {
                ast.strings.forEach(emit);
                ast.expressions.forEach((value) => classesIn(value, emit, unreadable));
            }
            break;
        default:
            unreadable();
    }
}

export default {
    meta: {
        type: 'problem',
        docs: { description: 'Keep TUM UI appearance in the package; use public inputs instead of consumer overrides.' },
        schema: [],
        messages: {
            appearance:
                '"{{value}}" restyles {{component}}. {{guidance}} Add a supported package capability if a required treatment is missing. Keep layout on the host or a wrapper.',
            internal: '"{{value}}" targets private TUM UI markup. Use a public input or fix the package instead of depending on its internal selectors.',
            opaque: 'Classes on {{component}} must be statically readable. Use complete class names in conditional bindings or [class.name]; use component inputs for appearance.',
            style: 'Style "{{value}}" on {{component}} bypasses its appearance contract. {{guidance}} Only host layout and --tumaet-ui-* theme properties belong here.',
            legacy: '{{component}} has no styleClass API. Use the native class attribute for host layout and public inputs for appearance.',
        },
    },
    create(context) {
        function checkElement(element) {
            const attributes = [...element.attributes, ...element.inputs];
            const component = element.name?.startsWith('tum-ui-')
                ? element.name
                : attributes.find((attribute) => ['tumUiButton', 'tumUiInput', 'tumUiTextarea'].includes(attribute.name))?.name;

            for (const attribute of attributes) {
                let opaqueReported = false;
                const report = (messageId, value) =>
                    context.report({ node: attribute, messageId, data: { value, component, guidance: GUIDANCE[component] ?? "Use the component's supported appearance inputs." } });
                const unreadable = () => {
                    if (component && !opaqueReported) {
                        opaqueReported = true;
                        report('opaque', '');
                    }
                };
                const checkClasses = (value) => {
                    for (const token of value.split(/\s+/).filter(Boolean)) {
                        // Also catch selectors placed on an ordinary ancestor, outside no-restyle's usual scope.
                        if (/^tum-ui-/.test(token) || (token.includes('[') && /(?:\.tum-ui-|\[tumUi[A-Z]|\[class[^\]]*tum-ui-)/.test(token))) {
                            report('internal', token);
                            continue;
                        }
                        if (!component) continue;
                        const utility = utilityOf(token);
                        const arbitraryProperty = utility.match(/^\[([^:]+):/);
                        if ((APPEARANCE_UTILITY.test(utility) && !TEXT_LAYOUT.test(utility)) || (arbitraryProperty && !isAllowedProperty(arbitraryProperty[1]))) {
                            report('appearance', token);
                        }
                    }
                };
                const checkProperty = (name) => {
                    const property = cssProperty(name);
                    if (!isAllowedProperty(property)) report('style', name);
                };
                const checkStyleText = (value) => {
                    try {
                        postcss.parse(`a {${value}}`).walkDecls((declaration) => checkProperty(declaration.prop));
                    } catch {
                        report('style', 'unreadable style');
                    }
                };

                if (component && /^(?:styleClass|.+StyleClass)$/.test(attribute.name)) {
                    report('legacy', attribute.name);
                    continue;
                }
                const key = attribute.keySpan?.details ?? '';
                if (key.startsWith('class.')) {
                    checkClasses(attribute.name);
                } else if (attribute.name === 'class' || attribute.name === 'ngClass') {
                    if (attribute.type === 'TextAttribute') checkClasses(attribute.value);
                    else classesIn(attribute.value, checkClasses, unreadable);
                } else if (component && key.startsWith('style.')) {
                    checkProperty(attribute.name);
                } else if (component && (attribute.name === 'style' || attribute.name === 'ngStyle')) {
                    if (attribute.type === 'TextAttribute') {
                        checkStyleText(attribute.value);
                    } else if (attribute.value.ast?.type === 'LiteralMap') {
                        attribute.value.ast.keys.forEach((entry) => (entry.kind === 'property' ? checkProperty(entry.key.split('.')[0]) : report('style', 'dynamic property')));
                    } else if (attribute.value.ast?.type === 'LiteralPrimitive' && typeof attribute.value.ast.value === 'string') {
                        checkStyleText(attribute.value.ast.value);
                    } else {
                        report('style', 'dynamic style object');
                    }
                }
            }
        }
        return { Element: checkElement };
    },
};
