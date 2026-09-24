// cn's grammar needs the project's theme to classify named spacing and to
// distinguish text/shadow scale tokens from colors.
import { categoryOf } from '../grammar/categories.mjs';
import { normalizeClass, OPACITY_MODIFIER } from '../grammar/classes.mjs';
import { mergeConfigs } from 'cn/config';
import { classifierFor, createClassifier, resolveCnConfig } from '../grammar/classifier.mjs';
import { declaresClass, themeVocabularyFor } from './theme.mjs';
// Longest prefix first: text-shadow-crisp is a text-shadow, not text
// "shadow-crisp".
const NAMESPACES = [
    {
        prefix: 'text-shadow-',
        namespace: 'text-shadow-',
        group: 'text-shadow',
        overColor: true,
    },
    {
        prefix: 'inset-shadow-',
        namespace: 'inset-shadow-',
        group: 'inset-shadow',
        overColor: true,
    },
    {
        prefix: 'drop-shadow-',
        namespace: 'drop-shadow-',
        group: 'drop-shadow',
        overColor: true,
    },
    { prefix: 'shadow-', namespace: 'shadow-', group: 'shadow', overColor: true },
    { prefix: 'text-', namespace: 'text-', group: 'font-size', overColor: false },
    {
        prefix: 'bg-',
        namespace: 'background-image-',
        group: 'bg-image',
        overColor: false,
    },
];
const ANIMATE_PREFIX = 'animate-';
const memos = new WeakMap();
const classifiers = new WeakMap();
function classifierForProject(fromFile) {
    const vocabulary = themeVocabularyFor(fromFile);
    if (!vocabulary) return classifierFor();
    let classifier = classifiers.get(vocabulary);
    if (!classifier) {
        classifier = vocabulary.spacing.length ? createClassifier(mergeConfigs(resolveCnConfig(), { extend: { theme: { spacing: vocabulary.spacing } } })) : classifierFor();
        classifiers.set(vocabulary, classifier);
    }
    return classifier;
}
function memoFor(vocabulary) {
    let memo = memos.get(vocabulary);
    if (!memo) {
        memo = new Map();
        memos.set(vocabulary, memo);
    }
    if (memo.size > 50_000) memo.clear();
    return memo;
}
// The value a utility looks up, without the opacity or line-height
// modifier. Null when there is nothing a namespace could name.
function valueOf(base, prefix) {
    const rest = base.slice(prefix.length);
    const modifier = rest.match(OPACITY_MODIFIER)?.[0] ?? '';
    const value = rest.slice(0, rest.length - modifier.length);
    if (!value || value.startsWith('[') || value.startsWith('(')) return null;
    return value;
}
function lookup(vocabulary, token) {
    const base = normalizeClass(token);
    const entry = NAMESPACES.find((n) => base.startsWith(n.prefix));
    if (!entry) return null;
    const value = valueOf(base, entry.prefix);
    if (!value) return null;
    if (!entry.overColor && vocabulary.tokens.has(value)) return null;
    return vocabulary.names.has(`${entry.namespace}${value}`) ? entry.group : null;
}
// The cn group a project's own @theme gives this class, or null when the
// theme says nothing about it.
export function themeGroupFor(fromFile, token) {
    if (!fromFile) return null;
    const vocabulary = themeVocabularyFor(fromFile);
    if (!vocabulary) return null;
    const memo = memoFor(vocabulary);
    let group = memo.get(token);
    if (group === undefined) {
        group = lookup(vocabulary, token);
        memo.set(token, group);
    }
    return group;
}
// cn groups only Tailwind's own animations, so that merging never drops a
// plugin's animate-once. A project's animation is one its CSS declares:
// --animate-shimmer in @theme, or animate-in from an @utility or selector.
export function animationGroupFor(fromFile, token) {
    if (!fromFile) return null;
    const base = normalizeClass(token);
    if (!base.startsWith(ANIMATE_PREFIX)) return null;
    const value = valueOf(base, ANIMATE_PREFIX);
    if (!value) return null;
    if (themeVocabularyFor(fromFile)?.names.has(base)) return 'animate';
    return declaresClass(fromFile, token) ? 'animate' : null;
}
// The classifier the rules use: cn configured with project spacing names,
// then theme disambiguation for text/shadow colors and custom animations.
export function projectClassifierFor(fromFile) {
    const { groupOf: grammarGroupOf } = classifierForProject(fromFile);
    const groupOf = (token) => {
        const group = grammarGroupOf(token);
        if (!group) return animationGroupFor(fromFile, token);
        if (categoryOf(group) !== 'color') return group;
        return themeGroupFor(fromFile, token) ?? group;
    };
    return { groupOf };
}
