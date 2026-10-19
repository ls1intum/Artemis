/**
 * Maps the ASCII emoticons that the Monaco editor's emoji conversion replaces to their
 * Unicode emoji. Only emoticons starting with ':' are listed, because the conversion only
 * processes words starting with ':'. The emoji completion action also uses this map to
 * suppress name completion while an exact emoticon is being typed.
 */
export const EMOTICON_TO_EMOJI: Record<string, string> = {
    ':o)': '🐵',
    ':D': '😄',
    ':-D': '😄',
    ':|': '😐',
    ':-|': '😐',
    ':\\': '😕',
    ':-\\': '😕',
    ':/': '😕',
    ':-/': '😕',
    ':*': '😘',
    ':-*': '😘',
    ':p': '😛',
    ':-p': '😛',
    ':P': '😛',
    ':-P': '😛',
    ':b': '😛',
    ':-b': '😛',
    ':(': '😞',
    ':-(': '😞',
    ":'(": '😢',
    ':o': '😮',
    ':-o': '😮',
    ':O': '😮',
    ':-O': '😮',
    ':)': '🙂',
    ':-)': '🙂',
};

/**
 * Matches an emoticon preceded by the start of the string or whitespace and followed by the end of
 * the string or a delimiter. Emoticons are ordered longest-first so e.g. ':o)' wins over ':o'.
 */
export const EMOTICON_REGEX = new RegExp(
    '(^|\\s)(' +
        Object.keys(EMOTICON_TO_EMOJI)
            .sort((a, b) => b.length - a.length)
            .map((emoticon) => emoticon.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
            .join('|') +
        ')(?=$|[\\s|?.,!])',
    'g',
);
