// no-unknown-classes: a class Tailwind does not know generates no CSS and
// fails silently. The project's own Tailwind answers, through the worker
// in ../tailwind; a configured theme must compile successfully.
import { categoryOf } from '../grammar/categories.mjs';
import { isMarkerClass, normalizeClass, splitClasses } from '../grammar/classes.mjs';
import { didYouMean } from '../grammar/similar.mjs';
import { projectClassifierFor } from '../project/namespaces.mjs';
import { colorTokensFor, knownClassesFor, tailwindEntryFor, themeFileFor, utilityPrefixesOf } from '../project/theme.mjs';
import { classSiteVisitors } from '../expressions.mjs';
import { TailwindVerificationError, unknownClasses } from '../tailwind/client.mjs';
import { compileVocabularyPolicy, configErrorVisitors } from './contracts.mjs';
import { classSuggestions } from './fixes.mjs';
import { displayPath, fileOf, reporter } from './messages.mjs';
import { colorValueOf } from './no-raw-colors.mjs';
import { policySchema } from './policy-schema.mjs';
const MESSAGES = {
    compilerUnavailable: '{{reason}}. Fix the configured Tailwind theme or compiler availability, then rerun lint; class verification has not completed.',
    unknownClass: '"{{className}}" is not a class this project\'s Tailwind knows, so no CSS is generated for it. Fix the spelling, or declare it with @utility in {{file}}.',
    unknownClassSuggest: '"{{className}}" is not a class this project\'s Tailwind knows, so no CSS is generated for it. Did you mean "{{suggestion}}"?',
    unknownVariant:
        '"{{className}}" uses a variant this project\'s Tailwind does not know, so no CSS is generated for it. Use an existing variant, or declare it with @custom-variant in {{file}}.',
    useSuggestion: 'Replace with "{{suggestion}}".',
};
export const noUnknownClasses = {
    meta: {
        type: 'problem',
        hasSuggestions: true,
        docs: {
            description: 'Disallow classes Tailwind does not know; no CSS is generated for them.',
            url: 'https://github.com/shadcn-ui/lint/blob/main/docs/rules/no-unknown-classes.md',
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
            rule: 'design-system/no-unknown-classes',
            message: options.message,
        });
        const filename = fileOf(context);
        let policy;
        try {
            policy = compileVocabularyPolicy({ ...options, uncheckedEntries: true, fromFile: filename }, 'design-system/no-unknown-classes');
        } catch (error) {
            return configErrorVisitors(context, error);
        }
        const { groupOf } = projectClassifierFor(filename);
        const known = knownClassesFor(filename);
        const themeFile = themeFileFor(filename);
        const file = themeFile ? displayPath(themeFile, context) : 'your theme CSS';
        // A theme file that does not import Tailwind knows no base utilities,
        // so the grammar answers instead of a half-built design system.
        const entry = tailwindEntryFor(filename);
        const utilityPrefixes = utilityPrefixesOf(known.utilities);
        // What the project's CSS settles without asking Tailwind.
        const settled = (token) => {
            const base = normalizeClass(token);
            if (!base) return true;
            if (base.startsWith('[')) return true;
            if (isMarkerClass(token)) return true;
            return known.classes.has(base.replace(/\/[\w.%]+$/, ''));
        };
        // Without Tailwind: the cn grammar plus @utility names.
        const knownByGrammar = (token) => {
            if (groupOf(token)) return true;
            const bare = normalizeClass(token).replace(/\/[\w.%]+$/, '');
            if (known.utilities.has(bare)) return true;
            return utilityPrefixes.some((prefix) => bare.startsWith(prefix));
        };
        const report = (node, token, suggestion, words, component, variantOnly = false) => {
            emit(
                {
                    node,
                    messageId: suggestion ? 'unknownClassSuggest' : variantOnly ? 'unknownVariant' : 'unknownClass',
                    data: {
                        className: token,
                        component,
                        file,
                        suggestion: suggestion ?? '',
                    },
                    suggest: suggestion ? classSuggestions(node, context, token, [suggestion], 'useSuggestion', 'suggestion') : undefined,
                },
                words,
            );
        };
        const isColor = (token) => categoryOf(groupOf(token)) === 'color';
        // Who owns a class the grammar files under a color that generates no
        // CSS: an undeclared token or a near-miss of one is no-raw-colors'
        // finding, a typo of another utility (text-smal) is this rule's.
        // no-raw-colors applies the same test, so it is reported once.
        let declared;
        const ownsColorTypo = (token, suggestion) => {
            if (!suggestion || isColor(suggestion)) return false;
            const value = colorValueOf(token);
            if (!value) return true;
            declared ??= colorTokensFor(filename);
            return !declared || !didYouMean(value, declared);
        };
        let compilerUnavailable = false;
        return classSiteVisitors(context, options, (site) => {
            if (compilerUnavailable) return;
            for (const { value, node } of site.vocabularyStrings) {
                const wordsFor = new Map();
                const tokens = splitClasses(value).filter((token) => {
                    if (settled(token)) return false;
                    const exemption = policy.decide(site.component, token);
                    if (exemption.kind === 'ok') return false;
                    wordsFor.set(token, exemption.message);
                    return true;
                });
                if (!tokens.length) continue;
                // The worker tells a misspelled variant on a real color from a
                // utility that only looks like one, prefix and all.
                let asked;
                try {
                    asked = entry ? unknownClasses(entry, tokens) : null;
                } catch (error) {
                    if (!(error instanceof TailwindVerificationError)) throw error;
                    compilerUnavailable = true;
                    context.report({ node, messageId: 'compilerUnavailable', data: { reason: error.message } });
                    return;
                }
                if (asked) {
                    for (const { token, suggestion, baseKnown } of asked) {
                        if (isColor(token) && !baseKnown && !ownsColorTypo(token, suggestion)) continue;
                        report(node, token, suggestion, wordsFor.get(token) ?? null, site.component ?? '', baseKnown && token.includes(':'));
                    }
                    continue;
                }
                for (const token of tokens) {
                    if (!knownByGrammar(token)) report(node, token, null, wordsFor.get(token) ?? null, site.component ?? '');
                }
            }
        });
    },
};
