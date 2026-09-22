// no-inline-styles: the style attribute is the oldest escape hatch around
// class-based enforcement. CSS custom properties are the exception, and a
// hardcoded color in one is that exception being laundered, so it is
// followed one hop.
import valueParser from 'postcss-value-parser';
import { parseColor } from '../grammar/colors.mjs';
import { objectEntries, resolveMemberValue } from '../expressions.mjs';
import { allowListOf, configErrorVisitors, ContractConfigError } from './contracts.mjs';
import { reporter } from './messages.mjs';
import { policySchema } from './policy-schema.mjs';
const COLOR_FUNCTION = /#[0-9a-f]{3,8}\b|\b(?:rgb|rgba|hsl|hsla|hwb|oklch|oklab|lab|lch|color|color-mix|light-dark)\(/i;
// URL payloads, quoted strings and comments are not color values.
function colorValueText(value) {
    const parsed = valueParser(value);
    parsed.walk((node) => {
        if (node.type === 'string' || node.type === 'comment' || (node.type === 'function' && node.value.toLowerCase() === 'url')) {
            node.type = 'word';
            node.value = ' ';
            delete node.nodes;
            return false;
        }
    });
    return parsed.toString();
}
// The value, or any leaf of it outside var(), read as a color.
export function hasRawColor(value) {
    const text = colorValueText(value);
    if (COLOR_FUNCTION.test(text) || parseColor(text) !== null) return true;
    const leaves = text.replace(/var\([^)]*\)/gi, ' ').split(/[\s,()/]+/);
    return leaves.some((leaf) => leaf && parseColor(leaf) !== null);
}
// Unwrap TypeScript assertions in normalized expressions.
function unwrap(node) {
    while (node && (node.type === 'TSAsExpression' || node.type === 'TSSatisfiesExpression' || node.type === 'TSNonNullExpression')) {
        node = node.expression;
    }
    return node;
}
// Property names as CSS spells them, so backgroundColor and
// background-color are one name. A custom property is its own name.
function cssPropertyName(name) {
    if (name.startsWith('--')) return name;
    return name.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`).toLowerCase();
}
// Names or globs (border-*, --chart-*). The mistake this option invites
// is a class, caught by shape: no standard property carries a digit.
function propertyMatcher(entries) {
    const patterns = (entries ?? []).map((entry) => {
        const custom = /^--[\w*-]+$/.test(entry);
        if (!custom && !/^[a-zA-Z*][a-zA-Z*-]*$/.test(entry)) {
            throw new ContractConfigError(
                `design-system/no-inline-styles: entry "${entry}" is not a CSS property name (backgroundColor, background-color, border-*, --chart-1), so it would match nothing.`,
            );
        }
        return new RegExp(`^${cssPropertyName(entry).replace(/\*/g, '.*')}$`);
    });
    return (property) => {
        const name = cssPropertyName(property);
        return patterns.some((pattern) => pattern.test(name));
    };
}
// The shared policy shape, over property names.
function compilePropertyPolicy(options) {
    const topAllow = allowListOf(options);
    const topDeny = options.deny ?? [];
    const compile = (policy, inherited) => ({
        allow: propertyMatcher(allowListOf(policy, inherited?.allow)),
        deny: propertyMatcher(policy.deny ?? inherited?.deny),
        message: (inherited ? policy.message : undefined) ?? null,
    });
    const baseline = compile(options);
    const contracts = (options.contracts ?? []).map((c) => {
        let pattern;
        try {
            pattern = new RegExp(c.pattern);
        } catch {
            throw new ContractConfigError(`Contract pattern "${c.pattern}" is not a valid regular expression.`);
        }
        return { pattern, ...compile(c, { allow: topAllow, deny: topDeny }) };
    });
    const policyFor = (component) => {
        for (let i = contracts.length - 1; i >= 0; i--) {
            if (contracts[i].pattern.test(component)) return contracts[i];
        }
        return baseline;
    };
    const decide = (component, property) => {
        const policy = component ? policyFor(component) : baseline;
        if (policy.deny(property)) return { exempt: false, message: policy.message };
        if (policy.allow(property)) return { exempt: true, message: null };
        return { exempt: false, message: policy.message };
    };
    // The component's words for a finding about no single property.
    const wordsFor = (component) => (component ? policyFor(component).message : null);
    return { decide, wordsFor };
}
// Inspect resolved expressions and lookup table values for hardcoded colors.
function carriesRawColor(node, context, seen = new Set()) {
    node = unwrap(node);
    if (!node) return false;
    switch (node.type) {
        case 'Literal':
            return typeof node.value === 'string' && hasRawColor(node.value);
        case 'TemplateLiteral': {
            // Preserve CSS context without joining fragments into a color name not in the source.
            const slots = node.expressions.map((_, index) => `\uFFFC${index}\uFFFC`);
            const text = node.quasis.map((quasi, index) => (quasi.value?.cooked ?? '') + (slots[index] ?? '')).join('');
            const visible = colorValueText(text);
            return hasRawColor(text) || node.expressions.some((expression, index) => visible.includes(slots[index]) && carriesRawColor(expression, context, seen));
        }
        case 'ConditionalExpression':
            return carriesRawColor(node.consequent, context, seen) || carriesRawColor(node.alternate, context, seen);
        case 'LogicalExpression':
            return carriesRawColor(node.left, context, seen) || carriesRawColor(node.right, context, seen);
        case 'ObjectExpression':
            return node.properties.some((p) => p.type === 'Property' && carriesRawColor(p.value, context, seen));
        case 'ArrayExpression':
            return node.elements.some((el) => carriesRawColor(el, context, seen));
        case 'MemberExpression':
            return carriesRawColor(node.object, context, seen);
        default:
            return false;
    }
}
const MESSAGES = {
    inlineStyle: 'Inline style sets {{property}}. Style through classes; use CSS custom properties for dynamic values.',
    dynamicStyle: 'Dynamic style object cannot be checked. Build it from CSS custom properties only.',
    customPropColor: 'Custom property {{property}} hardcodes a color. Define it as a theme token instead of injecting a raw value.',
    styleElement: 'A <style> element injects CSS outside the design system. Use classes, or declare the rule in your theme CSS.',
};
export const noInlineStyles = {
    meta: {
        type: 'problem',
        docs: {
            description: 'Disallow inline style attributes, except CSS custom properties.',
            url: 'https://github.com/shadcn-ui/lint/blob/main/docs/rules/no-inline-styles.md',
        },
        schema: [
            {
                type: 'object',
                properties: policySchema,
                additionalProperties: false,
            },
        ],
        messages: MESSAGES,
    },
    create(context) {
        const options = context.options?.[0] ?? {};
        const emit = reporter(context, MESSAGES, {
            rule: 'design-system/no-inline-styles',
            message: options.message,
        });
        let policy;
        try {
            policy = compilePropertyPolicy(options);
        } catch (error) {
            return configErrorVisitors(context, error);
        }
        // `reportAt` is the node diagnostics attach to.
        const check = (expr, reportAt, seen = new Set(), component = '') => {
            expr = unwrap(expr);
            if (!expr) return;
            if ((expr.type === 'Identifier' && expr.name === 'undefined') || (expr.type === 'Literal' && expr.value === null)) {
                return;
            }
            // Each branch is a style value of its own. The left of && is the
            // condition, not a value; both sides of || and ?? are values.
            if (expr.type === 'ConditionalExpression') {
                check(expr.consequent, expr.consequent, seen, component);
                check(expr.alternate, expr.alternate, seen, component);
                return;
            }
            if (expr.type === 'LogicalExpression') {
                if (expr.operator !== '&&') check(expr.left, expr.left, seen, component);
                check(expr.right, expr.right, seen, component);
                return;
            }
            if (expr.type === 'MemberExpression') {
                const found = resolveMemberValue(expr, context, seen);
                if (!('unresolved' in found) && found.value) {
                    return check(found.value, reportAt, seen, component);
                }
            }
            if (expr.type !== 'ObjectExpression') {
                emit({ node: reportAt, messageId: 'dynamicStyle', data: { component } }, policy.wordsFor(component));
                return;
            }
            const properties = new Map();
            for (const entry of objectEntries(expr, context, seen)) {
                if ('unknown' in entry) {
                    const prop = entry.unknown;
                    emit({ node: prop, messageId: 'dynamicStyle', data: { component } }, policy.wordsFor(component));
                    continue;
                }
                properties.set(entry.key, entry.value);
            }
            for (const [key, value] of properties) {
                const verdict = policy.decide(component, key);
                if (verdict.exempt) continue;
                if (!key.startsWith('--')) {
                    emit(
                        {
                            node: value,
                            messageId: 'inlineStyle',
                            data: { property: key, component },
                        },
                        verdict.message,
                    );
                } else if (carriesRawColor(value, context)) {
                    emit(
                        {
                            node: value,
                            messageId: 'customPropColor',
                            data: { property: key, component },
                        },
                        verdict.message,
                    );
                }
            }
        };
        return context.sourceCode.parserServices.designSystem.styleVisitors(check, (node) => emit({ node, messageId: 'styleElement' }));
    },
};
