package de.tum.cit.aet.artemis.deimos.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds text in a submission that imitates the markers fencing the untrusted payload in the prompt. The check is
 * deterministic and independent of the model, so it holds even for content the model never receives.
 */
final class DeimosFenceMarkers {

    /**
     * A line imitating one of the markers that fence the untrusted payload, {@code ---BEGIN UNTRUSTED DATA <id>---} or
     * {@code ---END UNTRUSTED DATA <id>---}. Any identifier, or none, and any surrounding text are accepted, and case and
     * whitespace (including Unicode spaces) may differ, so a marker in a comment is caught as well.
     * <p>
     * The scan has to stay linear in the size of the submission. The fixed {@code -{3}} prefix makes every start
     * position fail within three characters unless it reaches the whitespace, and the possessive quantifiers never give
     * back what they consumed, so a long run of hyphens or whitespace is scanned once rather than once per start
     * position.
     */
    private static final Pattern FENCE_MARKER_PATTERN = Pattern.compile("-{3}[\\s\\p{Z}]*+(?:BEGIN|END)[\\s\\p{Z}]++UNTRUSTED[\\s\\p{Z}]++DATA", Pattern.CASE_INSENSITIVE);

    // Every common line ending, so converting a file between them keeps the identity and number of its lines.
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\r\n|[\r\n]");

    private DeimosFenceMarkers() {
    }

    /**
     * Checks a single line, or a file path, for an imitated fence marker.
     *
     * @param text the text to check, without line breaks
     * @return whether it contains an imitated fence marker
     */
    static boolean containsFenceMarker(CharSequence text) {
        return FENCE_MARKER_PATTERN.matcher(text).find();
    }

    @FunctionalInterface
    interface MarkerLineConsumer {

        void accept(int lineNumber, String line);
    }

    /**
     * Calls {@code consumer} for every line of {@code content} that contains an imitated fence marker.
     * <p>
     * One matcher is reused with its region set to each line in turn, so the whole content is scanned in a single linear
     * pass and a line is only copied when it matches.
     *
     * @param content  the text to scan
     * @param consumer receives the 1-based line number and the text of each matching line, without its line terminator
     */
    static void forEachMarkerLine(String content, MarkerLineConsumer consumer) {
        Matcher matcher = FENCE_MARKER_PATTERN.matcher(content);
        Matcher lineBreakMatcher = LINE_BREAK_PATTERN.matcher(content);
        int lineStart = 0;
        int lineNumber = 1;
        while (lineStart <= content.length()) {
            boolean hasLineBreak = lineBreakMatcher.find(lineStart);
            int lineEnd = hasLineBreak ? lineBreakMatcher.start() : content.length();
            if (matcher.region(lineStart, lineEnd).find()) {
                consumer.accept(lineNumber, content.substring(lineStart, lineEnd));
            }
            lineStart = hasLineBreak ? lineBreakMatcher.end() : content.length() + 1;
            lineNumber++;
        }
    }
}
