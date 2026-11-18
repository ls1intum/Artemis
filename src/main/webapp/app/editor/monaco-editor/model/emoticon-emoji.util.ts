/**
 * Maps the ASCII emoticons that start with ':' to their Unicode emoji. Nothing replaces them in the text: the message editor
 * uses the map to tell an emoticon from the start of an emoji name, and suppresses name completion while an exact emoticon is
 * being typed.
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
