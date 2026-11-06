import { readFileSync, readdirSync } from 'node:fs';
import { URL } from 'node:url';

// Product action families, independent of emphasis (solid/outlined/text) and interaction state.
const SEVERITIES = { workflow: 'primary', authoring: 'warn', transfer: 'secondary', utility: 'secondary', analytics: 'info', positive: 'success', destructive: 'danger' };
const LEGACY = {
    default: 'secondary',
    primary: 'primary',
    secondary: 'secondary',
    success: 'success',
    info: 'info',
    warning: 'warn',
    danger: 'danger',
    light: 'secondary',
    dark: 'contrast',
};
const BUTTON_TYPES = {
    DEFAULT: 'secondary',
    PRIMARY: 'primary',
    SECONDARY: 'secondary',
    SUCCESS: 'success',
    WARNING: 'warn',
    ERROR: 'danger',
    INFO: 'info',
    PRIMARY_OUTLINE: 'primary',
    SUCCESS_OUTLINE: 'success',
    ERROR_OUTLINE: 'danger',
};
const LABEL_ATTRIBUTES = new Set(['jhiTranslate', 'label', 'buttonLabel']);
const CONTROL_NAMES = new Set(['button', 'tumaet-ui-button', 'jhi-button', 'jhi-confirm-button', 'p-button']);
let translations;

function translated(value) {
    if (!value.includes('.')) return value;
    if (!translations) {
        translations = new Map();
        const directory = new URL('../../../src/main/webapp/i18n/en/', import.meta.url);
        const flatten = (object, prefix = '') => {
            for (const [key, child] of Object.entries(object)) {
                const name = prefix ? `${prefix}.${key}` : key;
                if (typeof child === 'string') translations.set(name, child);
                else if (child && typeof child === 'object') flatten(child, name);
            }
        };
        for (const file of readdirSync(directory)
            .filter((name) => name.endsWith('.json'))
            .sort()) {
            flatten(JSON.parse(readFileSync(new URL(file, directory), 'utf8')));
        }
    }
    return translations.get(value) ?? value;
}

function intentForLabel(value) {
    // Separate formatted words for classification; this text is never rendered as HTML.
    const label = translated(value)
        .replace(/<[^>]*>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    if (/^(?:import|export|download)\b/i.test(label)) return 'transfer';
    if (/^(?:save (?:and|&) approve|approve|preview)\b/i.test(label)) return 'positive';
    if (/^(?:save|create)\b/i.test(label)) return 'workflow';
    if (/^(?:edit|rename|configure|open in editor)\b/i.test(label)) return 'authoring';
    if (/^(?:cancel|close|back|refresh)\b/i.test(label)) return 'utility';
    if (/^(?:delete|remove|purge)\b/i.test(label)) return 'destructive';
    if (/^(?:statistics|assessment dashboard)\b/i.test(label)) return 'analytics';
    // Reset, Submit, Start, Retry, arbitrary handler names and status labels are context dependent.
    return undefined;
}

function values(expression) {
    const ast = expression?.ast ?? expression;
    if (ast?.type === 'LiteralPrimitive' && typeof ast.value === 'string') return [ast.value];
    if (ast?.type === 'BindingPipe' && ['artemisTranslate', 'translate'].includes(ast.name)) return values(ast.exp);
    if (ast?.type === 'Interpolation') return [...ast.strings, ...ast.expressions.flatMap((item) => values(item) ?? [])];
    if (ast?.type === 'Conditional') {
        const yes = values(ast.trueExp);
        const no = values(ast.falseExp);
        return yes && no ? [...yes, ...no] : undefined;
    }
    if (ast?.type === 'PropertyRead' && ast.receiver?.type === 'PropertyRead' && ast.receiver.name === 'ButtonType') {
        return BUTTON_TYPES[ast.name] ? [BUTTON_TYPES[ast.name]] : undefined;
    }
    return undefined;
}

function attribute(element, name) {
    const fixed = element.attributes?.find((item) => item.name === name);
    if (fixed) return [fixed.value];
    return values(element.inputs?.find((item) => item.name === name)?.value);
}

function hasAttribute(element, name) {
    return [...(element.attributes ?? []), ...(element.inputs ?? [])].some((item) => item.name === name);
}

function isControl(element) {
    return CONTROL_NAMES.has(element.name) || (element.name === 'a' && (hasAttribute(element, 'tumAetUiButton') || /\bbtn\b/.test(attribute(element, 'class')?.[0] ?? '')));
}

function labels(element, root = true) {
    if (!root && isControl(element)) return [];
    const result = [];
    for (const name of LABEL_ATTRIBUTES) result.push(...(attribute(element, name) ?? []));
    if (['jhi-button', 'jhi-confirm-button'].includes(element.name)) result.push(...(attribute(element, 'title') ?? []));
    if (element.type === 'Text') result.push(element.value);
    else if (element.type === 'BoundText') result.push(...(values(element.value) ?? []));
    for (const child of element.children ?? []) result.push(...labels(child, false));
    for (const branch of element.branches ?? element.cases ?? []) result.push(...labels(branch, false));
    return result;
}

function severity(element) {
    if (hasAttribute(element, 'severity')) {
        const bound = element.inputs?.find((item) => item.name === 'severity')?.value?.ast;
        // A green completed indicator is separate from the actionable color. Only omit that branch
        // when the same condition demonstrably disables the control (directly or through OR).
        const completed = completedBranch(element, bound);
        if (completed) return values(bound[completed === 'trueExp' ? 'falseExp' : 'trueExp']);
        return attribute(element, 'severity');
    }
    if (hasAttribute(element, 'btnType')) return attribute(element, 'btnType');
    if (hasAttribute(element, 'buttonType')) return attribute(element, 'buttonType');
    if (hasAttribute(element, 'overrideSecondary')) return undefined;
    if (
        hasAttribute(element, 'tumAetUiButton') ||
        hasAttribute(element, 'pButton') ||
        hasAttribute(element, 'jhi-exercise-action-button') ||
        ['tumaet-ui-button', 'jhi-button', 'jhi-confirm-button', 'p-button'].includes(element.name)
    )
        return ['primary'];
    const classes = attribute(element, 'class')?.[0] ?? '';
    const match = /\bbtn-(?:outline-)?(primary|secondary|default|success|info|warning|danger|light|dark)\b/.exec(classes);
    return match ? [LEGACY[match[1]]] : undefined;
}

function expressionKey(ast) {
    if (ast?.type === 'ImplicitReceiver') return '';
    if (ast?.type === 'LiteralPrimitive') return JSON.stringify(ast.value);
    if (ast?.type === 'PropertyRead') {
        const receiver = expressionKey(ast.receiver);
        if (receiver !== undefined) return `${receiver}.${ast.name}`;
    }
    if (ast?.type === 'Call') {
        const receiver = expressionKey(ast.receiver);
        const args = ast.args.map(expressionKey);
        if (receiver !== undefined && args.every((value) => value !== undefined)) return `${receiver}(${args.join(',')})`;
    }
    if (ast?.type === 'Binary') {
        const left = expressionKey(ast.left);
        const right = expressionKey(ast.right);
        if (left !== undefined && right !== undefined) return `(${left}${ast.operation}${right})`;
    }
    if (ast?.type === 'PrefixNot') {
        const operand = expressionKey(ast.expression);
        if (operand !== undefined) return operand.startsWith('!') ? operand.slice(1) : `!${operand}`;
    }
    return undefined;
}

function disabledBy(ast, key) {
    return expressionKey(ast) === key || (ast?.type === 'Binary' && ast.operation === '||' && (disabledBy(ast.left, key) || disabledBy(ast.right, key)));
}

function completedBranch(element, expression) {
    if (expression?.type !== 'Conditional') return undefined;
    const key = expressionKey(expression.condition);
    if (!key) return undefined;
    const disabled = element.inputs?.find((item) => item.name === 'disabled')?.value?.ast;
    if (values(expression.trueExp)?.join() === 'success' && disabledBy(disabled, key)) return 'trueExp';
    const opposite = key.startsWith('!') ? key.slice(1) : `!${key}`;
    if (values(expression.falseExp)?.join() === 'success' && disabledBy(disabled, opposite)) return 'falseExp';
    return undefined;
}

function labelConditions(element, root = true) {
    if (!root && isControl(element)) return [];
    const collect = (expression) => {
        const ast = expression?.ast ?? expression;
        if (ast?.type === 'Conditional') return [ast];
        if (ast?.type === 'BindingPipe' && ['artemisTranslate', 'translate'].includes(ast.name)) return collect(ast.exp);
        if (ast?.type === 'Interpolation') return ast.expressions.flatMap(collect);
        return [];
    };
    const result = element.type === 'BoundText' ? collect(element.value) : [];
    if (element.type === 'IfBlock' && element.branches.length === 2 && element.branches[0].expression && !element.branches[1].expression) {
        result.push({
            condition: element.branches[0].expression.ast,
            trueExp: { type: 'Interpolation', strings: labels(element.branches[0]), expressions: [] },
            falseExp: { type: 'Interpolation', strings: labels(element.branches[1]), expressions: [] },
        });
    }
    for (const input of element.inputs ?? []) {
        if (LABEL_ATTRIBUTES.has(input.name) || (input.name === 'title' && ['jhi-button', 'jhi-confirm-button'].includes(element.name))) result.push(...collect(input.value));
    }
    for (const child of element.children ?? []) result.push(...labelConditions(child, false));
    for (const branch of element.branches ?? element.cases ?? []) result.push(...labelConditions(branch, false));
    return result;
}

function conditionalMismatch(element, declaration) {
    const input = element.inputs?.find((item) => ['severity', 'btnType', 'buttonType'].includes(item.name))?.value?.ast;
    if (input?.type !== 'Conditional') return declaration ? { unchecked: true } : undefined;
    const key = expressionKey(input.condition);
    const opposite = key?.startsWith('!') ? key.slice(1) : `!${key}`;
    const candidates = declaration ? [declaration] : labelConditions(element);
    const label = key ? candidates.find((item) => [key, opposite].includes(expressionKey(item.condition))) : undefined;
    if (!label) {
        const families = new Set(
            labels(element)
                .map(intentForLabel)
                .filter(Boolean)
                .map((intent) => SEVERITIES[intent]),
        );
        return declaration || families.size > 1 ? { unchecked: true } : undefined;
    }
    const inverted = expressionKey(label.condition) !== key;
    const completed = declaration ? undefined : completedBranch(element, input);
    for (const branch of ['trueExp', 'falseExp']) {
        if (branch === completed) continue;
        const labelBranch = inverted ? (branch === 'trueExp' ? 'falseExp' : 'trueExp') : branch;
        const intents = (values(label[labelBranch]) ?? []).map((value) => (declaration ? value : intentForLabel(value))).filter(Boolean);
        const expected = new Set(intents.map((intent) => SEVERITIES[intent]));
        const actual = values(input[branch]);
        if (expected.size && actual?.some((value) => !expected.has(value))) return { intent: intents.join('/'), expected: [...expected].join('/'), actual: actual.join('/') };
    }
    return undefined;
}

function classValues(expression) {
    const ast = expression?.ast ?? expression;
    if (ast?.type === 'LiteralMap') return ast.keys.map((key) => key.key);
    if (ast?.type === 'LiteralArray') return ast.expressions.flatMap(classValues);
    if (ast?.type === 'Conditional') return [...classValues(ast.trueExp), ...classValues(ast.falseExp)];
    if (ast?.type === 'Binary' && ['&&', '||', '??'].includes(ast.operation)) return [...classValues(ast.left), ...classValues(ast.right)];
    return values(ast) ?? [];
}

function localColor(element) {
    const colorClass = (classes) =>
        classes
            .split(/\s+/)
            .some(
                (token) =>
                    /(?:^|:)!?(?:btn(?:-|$)|p-button(?:-|$)|bg-|\[(?:color|background(?:-color)?|border-color|--tumaet-ui-button-[\w-]+):)/.test(token) ||
                    /(?:^|:)!?(?:text|border)-(?:white|black|current|inherit|transparent|primary|secondary|danger|success|warning|info|light|dark|state-|text-|brand-|button-|border(?:$|!)|red-|orange-|amber-|yellow-|lime-|green-|emerald-|teal-|cyan-|sky-|blue-|indigo-|violet-|purple-|fuchsia-|pink-|rose-|slate-|gray-|zinc-|neutral-|stone-|\((?!length:)|\[(?:#|(?:rgba?|hsla?|oklab|oklch|color|var)\(|color:))/.test(
                        token,
                    ),
            );
    for (const item of element.attributes ?? []) {
        if (item.name === 'class' && colorClass(item.value)) return item;
        if (item.name === 'style' && /(?:^|;)\s*(?:color|background(?:-color)?|border(?:-color)?|--tumaet-ui-button-[\w-]+)\s*:/.test(item.value)) return item;
    }
    for (const item of element.inputs ?? []) {
        if (
            item.keySpan?.details?.startsWith('style.') &&
            /^(?:color|background|background-color|backgroundColor|border-color|borderColor|--tumaet-ui-button-[\w-]+)$/.test(item.name)
        )
            return item;
        if (item.keySpan?.details?.startsWith('class.') && colorClass(item.name)) return item;
        if (['class', 'ngClass', 'styleClass'].includes(item.name) && classValues(item.value).some(colorClass)) return item;
        if (
            item.name === 'style' &&
            /(?:^|[;{,])\s*['"]?(?:color|background(?:-color|Color)?|border(?:-color|Color)?|--tumaet-ui-button-[\w-]+)['"]?\s*:/.test(item.value?.source ?? '')
        )
            return item;
    }
    return undefined;
}

export default {
    meta: {
        type: 'problem',
        docs: { description: 'Enforce documented button action colors and prevent local TUM button palette overrides.' },
        schema: [],
        messages: {
            wrongSeverity:
                "Button intent '{{intent}}' requires '{{expected}}', but this button uses '{{actual}}'. Use the shared palette/API; document a contextual exception with data-button-intent and data-button-intent-reason.",
            missingReason: 'A button intent that differs from its label (or represents selection) needs a static data-button-intent-reason explaining the behavior.',
            unknownIntent: "Unknown button intent '{{intent}}'. Use workflow, authoring, transfer, utility, analytics, positive, destructive or selection.",
            localColor: 'Do not override TUM button colors with local classes/styles. Use severity/btnType and the global button palette; use size/variant for emphasis.',
            dynamicSeverity: 'An explicit button intent needs a statically checkable severity/btnType. Keep conditional action intents in dedicated, behavior-tested controls.',
            dynamicIntent: 'Button intent must be a literal family or a conditional family with a matching color condition and a static explanation.',
            ariaOnHost:
                'Use the ariaLabel input on tumaet-ui-button. aria-label on its wrapper does not name the native button inside it; native button/a directives keep their normal aria-label attribute.',
            conditionalIntent:
                'Conditional button labels and colors must use matching conditions, or be split into explicitly intent-declared controls. The mapping cannot be verified statically.',
        },
    },
    create(context) {
        return {
            Element(element) {
                if (!isControl(element)) return;
                if (element.name === 'tumaet-ui-button') {
                    const misplaced = [...(element.attributes ?? []), ...(element.inputs ?? [])].find((item) => item.name === 'aria-label');
                    if (misplaced) context.report({ node: misplaced, messageId: 'ariaOnHost' });
                }
                if (hasAttribute(element, 'tumAetUiButton') || hasAttribute(element, 'jhi-exercise-action-button') || element.name === 'tumaet-ui-button') {
                    const override = localColor(element);
                    if (override) context.report({ node: override, messageId: 'localColor' });
                }
                if (['tab', 'radio', 'checkbox', 'switch', 'option'].includes(attribute(element, 'role')?.[0])) return;
                const inferred = new Set(labels(element).map(intentForLabel).filter(Boolean));
                if (!inferred.size) {
                    for (const name of ['ariaLabel', 'aria-label', 'title']) {
                        for (const value of attribute(element, name) ?? []) {
                            const intent = intentForLabel(value);
                            if (intent) inferred.add(intent);
                        }
                    }
                }
                const explicit = attribute(element, 'data-button-intent');
                if (hasAttribute(element, 'data-button-intent') && !explicit?.length) {
                    context.report({ node: element, messageId: 'dynamicIntent' });
                    return;
                }
                const unknown = explicit?.find((intent) => !SEVERITIES[intent] && intent !== 'selection');
                if (unknown !== undefined || (explicit?.includes('selection') && explicit.length > 1)) {
                    context.report({ node: element, messageId: 'unknownIntent', data: { intent: unknown ?? explicit.join('/') } });
                    return;
                }
                if (explicit && (explicit.includes('selection') || explicit.length > 1 || [...inferred].some((intent) => !explicit.includes(intent)))) {
                    const reasons = attribute(element, 'data-button-intent-reason');
                    if (reasons?.length !== 1 || !reasons[0].trim()) {
                        context.report({ node: element, messageId: 'missingReason' });
                        return;
                    }
                }
                if (explicit?.[0] === 'selection') return;
                const declaration =
                    explicit && new Set(explicit.map((intent) => SEVERITIES[intent])).size > 1
                        ? element.inputs?.find((item) => item.name === 'data-button-intent')?.value?.ast
                        : undefined;
                if (!explicit || declaration) {
                    const mismatch = conditionalMismatch(element, declaration);
                    if (mismatch) {
                        if (mismatch.unchecked) context.report({ node: element, messageId: 'conditionalIntent' });
                        else context.report({ node: element, messageId: 'wrongSeverity', data: mismatch });
                        return;
                    }
                }
                const intents = explicit ?? [...inferred];
                const expected = new Set(intents.map((intent) => SEVERITIES[intent]));
                const actual = declaration ? values(element.inputs?.find((item) => ['severity', 'btnType', 'buttonType'].includes(item.name))?.value) : severity(element);
                if (explicit && !actual) {
                    context.report({ node: element, messageId: 'dynamicSeverity' });
                    return;
                }
                if (expected.size && actual && (actual.some((value) => !expected.has(value)) || [...expected].some((value) => !actual.includes(value)))) {
                    context.report({ node: element, messageId: 'wrongSeverity', data: { intent: intents.join('/'), expected: [...expected].join('/'), actual: actual.join('/') } });
                }
            },
        };
    },
};
