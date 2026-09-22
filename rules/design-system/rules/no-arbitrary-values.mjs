// no-arbitrary-values: p-[13px] is a value the theme does not have.
import { categoryOf } from '../grammar/categories.mjs';
import { isArbitraryValue, normalizeClass, splitClasses, splitVariants, withBase } from '../grammar/classes.mjs';
import { lengthInPx } from '../grammar/lengths.mjs';
import { projectClassifierFor } from '../project/namespaces.mjs';
import { colorTokensFor, colorValuesFor, scaleFor, spacingBaseFor, themeFileFor } from '../project/theme.mjs';
import { classSiteVisitors } from '../expressions.mjs';
import { compileVocabularyPolicy, configErrorVisitors } from './contracts.mjs';
import { classSuggestions } from './fixes.mjs';
import { displayPath, fileOf, listTokens, reporter } from './messages.mjs';
import { policySchema } from './policy-schema.mjs';
import { arbitraryColor, formatSteps, nearestColorTokens, nearestSteps, roleOf } from './suggest.mjs';
// A utility name may have several segments: border-spacing-x-[8px].
const PX_SPACING = /^(-?)([a-z]+(?:-[a-z]+)*)-\[(\d+(?:\.\d+)?)px\]$/;
// Layout groups on the spacing scale, so w-[200px] is named as w-50.
const SPACING_GROUPS = new Set([
    'm',
    'mx',
    'my',
    'ms',
    'me',
    'mt',
    'mr',
    'mb',
    'ml',
    'w',
    'min-w',
    'max-w',
    'h',
    'min-h',
    'max-h',
    'size',
    'inset',
    'inset-x',
    'inset-y',
    'start',
    'end',
    'top',
    'right',
    'bottom',
    'left',
    'translate',
    'translate-x',
    'translate-y',
    'basis',
    'indent',
    'leading',
    'scroll-m',
    'scroll-mx',
    'scroll-my',
    'scroll-mt',
    'scroll-mr',
    'scroll-mb',
    'scroll-ml',
    'scroll-p',
    'scroll-px',
    'scroll-py',
    'scroll-pt',
    'scroll-pr',
    'scroll-pb',
    'scroll-pl',
]);
// "rounded-tl-[10px]" -> utility "rounded-tl", inner "10px", suffix "".
// "text-[13px]/5" keeps its "/5" line-height as the suffix.
export function splitArbitrary(token) {
    const match = normalizeClass(token).match(/^(.+?)-\[([^\]]*)\](.*)$/);
    return match ? { utility: match[1], inner: match[2], suffix: match[3] } : null;
}
// p-[13px] -> p-3.25, when it lands on a clean quarter step of the
// project's spacing unit.
export function scaleEquivalent(token, unitPx = 4) {
    if (unitPx === null) return null;
    const { variants, base } = splitVariants(token);
    const leading = base.startsWith('!') ? '!' : '';
    const trailing = !leading && base.endsWith('!') ? '!' : '';
    const bare = base.slice(leading.length, base.length - trailing.length);
    const match = bare.match(PX_SPACING);
    if (!match) return null;
    const [, negative, utility, px] = match;
    const steps = Number(px) / unitPx;
    if (!Number.isInteger(steps * 4)) return null;
    const prefix = variants.length ? `${variants.join(':')}:` : '';
    return `${prefix}${leading}${negative}${utility}-${steps}${trailing}`;
}
const MESSAGES = {
    arbitraryValue: '"{{className}}" hardcodes an off-token value. Use a theme token or scale value instead.',
    arbitraryValueWithScale: '"{{className}}" hardcodes an off-token value. Use "{{replacement}}" instead (same value, on the scale).',
    arbitraryValueNearScale: '"{{className}}" hardcodes an off-token value. Nearest on the scale: {{suggestions}}.',
    arbitraryColorNear: '"{{className}}" hardcodes a color. Nearest theme tokens: {{suggestions}}. Use one of those, or declare --color-<name> in {{file}}.',
    arbitraryColorFar: '"{{className}}" hardcodes a color and no declared theme color is close to it. Use one of: {{tokens}}, or declare --color-<name> in {{file}}.',
    useScale: 'Replace with "{{replacement}}" (same value, on the scale).',
    useToken: 'Replace with "{{replacement}}".',
};
export const noArbitraryValues = {
    meta: {
        type: 'problem',
        hasSuggestions: true,
        docs: {
            description: 'Disallow arbitrary values on appearance utilities; use theme tokens and scale values.',
            url: 'https://github.com/shadcn-ui/lint/blob/main/docs/rules/no-arbitrary-values.md',
        },
        schema: [
            {
                type: 'object',
                properties: {
                    ...policySchema,
                },
                additionalProperties: false,
            },
        ],
        messages: MESSAGES,
    },
    create(context) {
        const options = context.options?.[0] ?? {};
        const emit = reporter(context, MESSAGES, {
            rule: 'design-system/no-arbitrary-values',
            message: options.message,
        });
        const filename = fileOf(context);
        let policy;
        try {
            policy = compileVocabularyPolicy({ ...options, fromFile: filename }, 'design-system/no-arbitrary-values');
        } catch (error) {
            return configErrorVisitors(context, error);
        }
        const { groupOf } = projectClassifierFor(filename);
        let file;
        let unitPx;
        // Null when the theme's values cannot be read.
        const colorSuggestions = (token, parts) => {
            const lab = arbitraryColor(parts.inner);
            const colors = lab ? colorValuesFor(filename) : null;
            if (!lab || !colors?.size) return null;
            return nearestColorTokens(lab, colors, roleOf(`${parts.utility}-`)).map((name) => withBase(token, `${parts.utility}-${name}${parts.suffix}`));
        };
        return classSiteVisitors(context, options, (site) => {
            for (const { value, node } of site.vocabularyStrings) {
                for (const token of splitClasses(value)) {
                    if (!isArbitraryValue(token)) continue;
                    const exemption = policy.decide(site.component, token);
                    if (exemption.kind === 'ok') continue;
                    if (file === undefined) {
                        const themeFile = themeFileFor(filename);
                        file = themeFile ? displayPath(themeFile, context) : 'your theme CSS';
                    }
                    const group = groupOf(token);
                    const category = categoryOf(group);
                    const parts = splitArbitrary(token);
                    if (category === 'color' && parts) {
                        const suggestions = colorSuggestions(token, parts);
                        if (suggestions) {
                            emit(
                                {
                                    node,
                                    messageId: suggestions.length ? 'arbitraryColorNear' : 'arbitraryColorFar',
                                    data: {
                                        className: token,
                                        component: site.component ?? '',
                                        suggestions: suggestions.join(', '),
                                        tokens: listTokens(colorTokensFor(filename) ?? new Set()),
                                        file,
                                    },
                                    suggest: classSuggestions(node, context, token, suggestions, 'useToken', 'replacement'),
                                },
                                exemption.message,
                            );
                            continue;
                        }
                    }
                    let replacement = null;
                    let near = null;
                    const scaleKind = group === 'font-size' ? 'text' : group && /^rounded(-|$)/.test(group) ? 'radius' : null;
                    if (category === 'spacing' || (group && SPACING_GROUPS.has(group))) {
                        if (unitPx === undefined) unitPx = spacingBaseFor(filename);
                        replacement = scaleEquivalent(token, unitPx);
                    } else if (scaleKind && parts) {
                        const px = lengthInPx(parts.inner.replace(/_/g, ' '));
                        const steps = px === null ? [] : nearestSteps(px, scaleFor(filename, scaleKind));
                        const toClass = (name) => withBase(token, `${parts.utility}-${name}${parts.suffix}`);
                        if (steps[0]?.exact) replacement = toClass(steps[0].name);
                        else if (steps.length) near = formatSteps(steps, toClass);
                    }
                    emit(
                        {
                            node,
                            messageId: replacement ? 'arbitraryValueWithScale' : near ? 'arbitraryValueNearScale' : 'arbitraryValue',
                            data: {
                                className: token,
                                component: site.component ?? '',
                                replacement: replacement ?? '',
                                suggestions: near ?? '',
                                file,
                            },
                            suggest: replacement ? classSuggestions(node, context, token, [replacement], 'useScale', 'replacement') : undefined,
                        },
                        exemption.message,
                    );
                }
            }
        });
    },
};
