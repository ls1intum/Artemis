package de.tum.cit.aet.artemis.exercise.service;

import java.util.function.IntFunction;

/**
 * Substitutes {@code <prefix><index><suffix>} placeholders in a single pass.
 * <p>
 * The renderer masks code blocks, formulas and diagrams as placeholders and puts them back afterwards. Doing that with one
 * {@link String#replace} per placeholder costs a pass over the whole document each time, which is quadratic in the number
 * of placeholders: a 60 KB line of alternating dollar signs produces one formula per pair and took over two seconds to put
 * back, against eight milliseconds for every other input of that size. Scanning once is linear.
 * <p>
 * No placeholder this substitutes can be written by the document itself. The code block and formula placeholders are
 * delimited by NUL, which {@code ProblemStatementRenderRequestDTO} rejects in the request, so a document cannot bring its
 * own. The PlantUML SVG placeholder is a plain {@code <span>} carrying {@code data-svg-index}, which the safelist does
 * keep, so that one is made unforgeable instead by a per-render token the caller puts into the prefix: an author writes
 * their markdown before the token exists, so a hand-written span fails to match and is left as the inert markup it is.
 * Without that token a single diagram could be duplicated into as many copies as fit in the request, which is the one way
 * past the renderer's diagram limit.
 */
final class IndexedPlaceholders {

    private IndexedPlaceholders() {
    }

    /**
     * @param text        the text holding the placeholders
     * @param prefix      the placeholder prefix, immediately followed by the decimal index
     * @param suffix      the placeholder suffix
     * @param count       how many placeholders were handed out; a higher index is not one of ours and stays as text
     * @param replacement supplies the replacement for an index
     * @return the text with every placeholder replaced
     */
    static String replaceAll(String text, String prefix, String suffix, int count, IntFunction<String> replacement) {
        if (count == 0) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int copiedUpTo = 0;
        int start = text.indexOf(prefix);
        while (start >= 0) {
            int indexStart = start + prefix.length();
            int indexEnd = text.indexOf(suffix, indexStart);
            int index = indexEnd < 0 ? -1 : parseIndex(text, indexStart, indexEnd, count);
            if (index < 0) {
                // Not a placeholder of ours. Skipped over, so that the search cannot get stuck on it.
                start = text.indexOf(prefix, indexStart);
                continue;
            }
            result.append(text, copiedUpTo, start).append(replacement.apply(index));
            copiedUpTo = indexEnd + suffix.length();
            start = text.indexOf(prefix, copiedUpTo);
        }
        result.append(text, copiedUpTo, text.length());
        return result.toString();
    }

    /**
     * @return the decimal number between the two positions, or {@code -1} when it is not a number below {@code count}
     */
    private static int parseIndex(String text, int from, int to, int count) {
        if (from == to || to - from > 9) {
            return -1;
        }
        int index = 0;
        for (int position = from; position < to; position++) {
            int digit = Character.digit(text.charAt(position), 10);
            if (digit < 0) {
                return -1;
            }
            index = index * 10 + digit;
        }
        return index < count ? index : -1;
    }
}
