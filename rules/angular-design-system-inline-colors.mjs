import { categoryOf } from './design-system/grammar/categories.mjs';
import { groupOf } from './design-system/grammar/classifier.mjs';
import { objectEntries } from './design-system/expressions.mjs';
import { hasRawColor } from './design-system/rules/no-inline-styles.mjs';
import { resolveVariables } from './design-system/project/theme.mjs';

function branches(expression, visit) {
    if (expression?.type === 'ConditionalExpression') {
        branches(expression.consequent, visit);
        branches(expression.alternate, visit);
    } else if (expression?.type === 'LogicalExpression') {
        if (expression.operator !== '&&') branches(expression.left, visit);
        branches(expression.right, visit);
    } else if (expression) visit(expression);
}

/** Literal UI colors belong to theme tokens; runtime domain data and layout remain outside this rule. */
export function createLiteralInlineColorsRule(adapterFor) {
    return {
        meta: {
            type: 'problem',
            schema: [],
            messages: {
                rawColor:
                    'Inline {{property}} hardcodes "{{value}}". Use a semantic class such as text-state-danger, text-state-success or text-muted-color for status feedback, or an existing theme token for other UI colors. Runtime domain colors may stay bound.',
            },
        },
        create(context) {
            return adapterFor(context).styleVisitors(
                (expression, at, _seen, component) => {
                    if (component) return;
                    const reported = new Set();
                    branches(expression, (object) => {
                        if (object.type !== 'ObjectExpression') return;
                        const entries = objectEntries(object);
                        const localValues = new Map();
                        for (const entry of entries) {
                            if ('unknown' in entry) localValues.clear();
                            else if (entry.key.startsWith('--')) {
                                localValues.delete(entry.key.slice(2));
                                if (entry.value.type === 'Literal' && typeof entry.value.value === 'string') localValues.set(entry.key.slice(2), entry.value.value);
                            }
                        }
                        for (const entry of entries) {
                            if ('unknown' in entry) continue;
                            const property = entry.key.startsWith('--') ? entry.key : entry.key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
                            if (categoryOf(groupOf(`[${property}:inherit]`)) !== 'color') continue;
                            branches(entry.value, (value) => {
                                if (value.type !== 'Literal' || typeof value.value !== 'string') return;
                                const resolved = resolveVariables(value.value, localValues) ?? value.value;
                                if (!hasRawColor(resolved)) return;
                                const key = `${property}:${value.value}`;
                                if (reported.has(key)) return;
                                reported.add(key);
                                context.report({
                                    node: at,
                                    messageId: 'rawColor',
                                    data: { property, value: resolved === value.value ? value.value : `${value.value} → ${resolved}` },
                                });
                            });
                        }
                    });
                },
                () => {},
                { includeUnprotected: true },
            );
        },
    };
}
