// A suggestion rewrites one class inside a literal's own source text,
// never re-serializing it, so quotes, escapes and entities survive. A
// class not found verbatim there gets no suggestion rather than a wrong
// one.
import { replaceClass } from '../grammar/classes.mjs';
export function replaceInLiteral(node, context, token, replacement) {
    if (node?.type !== 'Literal' || typeof node.value !== 'string') return null;
    const raw = node.raw ?? context.sourceCode?.getText?.(node) ?? '';
    const quote = raw[0];
    if ((quote !== '"' && quote !== "'") || raw[raw.length - 1] !== quote) {
        return null;
    }
    const inner = raw.slice(1, -1);
    // A suggestion is safe only when the parsed literal equals its source text.
    if (inner !== node.value) return null;
    const replaced = replaceClass(inner, token, replacement);
    return replaced === inner ? null : `${quote}${replaced}${quote}`;
}
export function classSuggestion(node, context, token, replacement, messageId, data) {
    const text = replaceInLiteral(node, context, token, replacement);
    if (text === null) return null;
    return {
        messageId,
        data,
        fix: (fixer) => fixer.replaceText(node, text),
    };
}
// Undefined rather than an empty list, so a report carries none.
export function classSuggestions(node, context, token, replacements, messageId, key) {
    const list = replacements
        .map((replacement) =>
            classSuggestion(node, context, token, replacement, messageId, {
                [key]: replacement,
            }),
        )
        .filter((s) => s !== null);
    return list.length ? list : undefined;
}
