package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Applies the model's {@code oldText} / {@code newText} edit to a file's content.
 * <p>
 * The model edits from a copy of the file it read some turns ago, so the text it sends back is often close to the file rather than equal to it. This resolves that near-miss into
 * exact bytes, or into an error the model can act on, and it does so purely: no sandbox, no session, no stage. That is what makes it testable on strings alone.
 */
final class AgentTextEdit {

    private AgentTextEdit() {
    }

    /** Either the updated file content or an agent-actionable {@code ERROR:} message; exactly one side is set. */
    record Outcome(@Nullable String content, @Nullable String error) {

        static Outcome updated(String content) {
            return new Outcome(content, null);
        }

        static Outcome failed(String error) {
            return new Outcome(null, error);
        }
    }

    /**
     * Tries the exact text first and only then a normalized match (see {@link #normalizeForTolerantMatch}), because a tolerant match must never win over a byte-exact one. A
     * normalized match is accepted only when it is unique, and only the lines it touches are rewritten from the normalized text; every other line keeps its original bytes.
     *
     * @param safe    the workspace-relative path, used only in the error messages the model reads
     * @param current the file's current content
     * @param oldText the text the model wants replaced
     * @param newText the replacement text
     * @return the rewritten content, or an actionable error if the match is missing or ambiguous
     */
    static Outcome applyUniqueReplacement(String safe, String current, String oldText, String newText) {
        int occurrences = countOccurrences(current, oldText);
        if (occurrences > 1) {
            return Outcome.failed("ERROR: the provided oldText occurs " + occurrences + " times in '" + safe + "'. Provide more surrounding context to make it unique.");
        }
        if (occurrences == 1) {
            int first = current.indexOf(oldText);
            return Outcome.updated(current.substring(0, first) + newText + current.substring(first + oldText.length()));
        }
        String normalizedCurrent = normalizeForTolerantMatch(current);
        String normalizedOld = normalizeForTolerantMatch(oldText);
        int normalizedOccurrences = normalizedOld.isEmpty() ? 0 : countOccurrences(normalizedCurrent, normalizedOld);
        if (normalizedOccurrences > 1) {
            return Outcome.failed("ERROR: the provided oldText occurs " + normalizedOccurrences + " times in '" + safe + "' (ignoring whitespace-only differences). "
                    + "Provide more surrounding context to make it unique.");
        }
        if (normalizedOccurrences == 0) {
            return Outcome.failed("ERROR: the provided oldText was not found in '" + safe + "'. It must match the file exactly, including whitespace and newlines. "
                    + "Read the file again to get the exact current text.");
        }
        return spliceNormalizedMatch(current, normalizedCurrent, normalizedCurrent.indexOf(normalizedOld), normalizedOld.length(), newText);
    }

    /**
     * Rebuilds the file after a tolerant match: lines outside the matched range keep their original bytes, and only the matched range is emitted from the normalized text. Bails
     * out rather than corrupt the file if normalization changed the line structure, which {@link #normalizeForTolerantMatch} guarantees it does not.
     */
    private static Outcome spliceNormalizedMatch(String current, String normalizedCurrent, int matchIndex, int matchLength, String newText) {
        String[] originalLines = current.split("\n", -1);
        String[] normalizedLines = normalizedCurrent.split("\n", -1);
        if (originalLines.length != normalizedLines.length) {
            return Outcome.failed("ERROR: the provided oldText was not found in the file. Read the file again to get the exact current text.");
        }
        int matchEnd = matchIndex + matchLength;
        int lineStartOffset = 0;
        int startLine = 0;
        while (lineStartOffset + normalizedLines[startLine].length() < matchIndex) {
            lineStartOffset += normalizedLines[startLine].length() + 1;
            startLine++;
        }
        int endLine = startLine;
        int endLineStartOffset = lineStartOffset;
        while (endLineStartOffset + normalizedLines[endLine].length() < matchEnd) {
            endLineStartOffset += normalizedLines[endLine].length() + 1;
            endLine++;
        }
        int lineEndOffset = endLineStartOffset + normalizedLines[endLine].length();
        String replacedBlock = normalizedCurrent.substring(lineStartOffset, matchIndex) + newText + normalizedCurrent.substring(matchEnd, lineEndOffset);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < startLine; i++) {
            result.append(originalLines[i]).append('\n');
        }
        result.append(replacedBlock);
        for (int i = endLine + 1; i < originalLines.length; i++) {
            result.append('\n').append(originalLines[i]);
        }
        return Outcome.updated(result.toString());
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        for (int index = content.indexOf(needle); index >= 0; index = content.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }

    /**
     * Folds the mismatches a model introduces when re-typing code it read earlier — NFKC, trailing whitespace per line, smart quotes, Unicode dashes and spaces — so a near-miss
     * edit succeeds instead of bouncing a "not found" error back for a byte-identical retry. Must never add or remove a newline: {@link #spliceNormalizedMatch} relies on line
     * indices staying aligned with the original text.
     *
     * @param text the text to fold
     * @return the folded text, with the same number of newlines as the input
     */
    static String normalizeForTolerantMatch(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("[\u2018\u2019\u201A\u201B]", "'").replaceAll("[\u201C\u201D\u201E\u201F]", "\"")
                .replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]", "-").replaceAll("[\u00A0\u2002-\u200A\u202F\u205F\u3000]", " ");
        return Arrays.stream(folded.split("\n", -1)).map(line -> line.replaceAll("[ \t]+$", "")).collect(Collectors.joining("\n"));
    }
}
