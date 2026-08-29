package de.tum.cit.aet.artemis.exercise.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts LaTeX math formulas from markdown and, after sanitization, injects server-generated MathML.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>{@link #applyCompatibility(String)}: ports the Angular {@code FormulaCompatibilityPlugin} so authors
 * can write lines that mix inline math with surrounding text using {@code $$...$$}.</li>
 * <li>{@link #extract(String, List)}: replaces display and inline formulas with opaque NUL placeholders so
 * downstream CommonMark rendering does not mangle them.</li>
 * <li>{@link #restore(String, List, String)}: replaces those with a token-guarded marker span that survives
 * CommonMark and the jsoup safelist, exactly like the PlantUML SVG placeholder.</li>
 * <li>{@link #injectMathml(String, List, String)}: after jsoup, replaces each marker with the sanitized MathML
 * from {@link LatexToMathmlConverter}, or the escaped source when conversion fails or a limit is exceeded. This
 * runs post-jsoup and independent of {@code includeJs}: native MathML needs no client script.</li>
 * </ul>
 */
public final class MathFormulaExtractor {

    /**
     * Inline math: {@code $...$}, with the surrounding characters restricted so the body cannot contain
     * another {@code $} or a newline. Using {@code [^$\n]+} instead of a lazy {@code .+?} makes the match
     * deterministic and prevents O(N²) backtracking on crafted input.
     */
    private static final Pattern INLINE_MATH_PATTERN = Pattern.compile("(?<!\\$)\\$(?!\\$)([^$\n]+)\\$(?!\\$)");

    private static final String PLACEHOLDER_PREFIX = "\u0000MATH_";

    private static final String PLACEHOLDER_SUFFIX = "\u0000";

    /**
     * Marker that survives CommonMark and the jsoup safelist ({@code data-formula-index} is on the span safelist),
     * carrying the per-render token so an author cannot forge it. Distinct from the SVG placeholder's prefix and
     * attribute even though it shares the same per-render token, so the two indexed lists cannot be confused. The
     * MathML itself is injected only afterwards, so the marker carries no formula source of its own.
     */
    private static final String MARKER_PREFIX = "<span class=\"artemis-formula-placeholder\" data-formula-index=\"";

    private static final String MARKER_SUFFIX = "\"></span>";

    /** Above this many formulas in one statement, none are converted (all fall back to source) to bound per-request cost. */
    private static final int MAX_FORMULAS_TO_CONVERT = 500;

    /** Parsed formula held between extraction and restoration. */
    public record Formula(String latex, boolean displayMode) {
    }

    private MathFormulaExtractor() {
    }

    /**
     * Normalizes authoring quirks so downstream extraction sees a consistent formula syntax.
     * If a line contains {@code $$...$$} mixed with surrounding text, rewrite every {@code $$} on that
     * line to a single {@code $} (making it inline math). Also normalizes {@code \\begin}/{@code \\end}
     * to {@code \begin}/{@code \end}.
     * <p>
     * Split on LF alone, and the body of the formula may hold no dollar sign, because this mirrors the client's
     * {@code FormulaCompatibilityPlugin} - {@code text.split('\n')} and {@code /.+\$\$[^$]+\$\$|\$\$[^$]+\$\$.+/} - and
     * the two renderers have to agree on what an author's markdown means. Text on the other side of a bare carriage
     * return does not count as surrounding text, because the client's {@code .} matches no line terminator either; see
     * {@link #usesInlineConvention}.
     *
     * @param markdown the markdown source
     * @return the markdown with formula-authoring quirks normalized
     */
    public static String applyCompatibility(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (usesInlineConvention(withoutCarriageReturn(line))) {
                line = line.replace("$$", "$");
            }
            if (line.contains("\\\\begin") || line.contains("\\\\end")) {
                line = line.replace("\\\\begin", "\\begin").replace("\\\\end", "\\end");
            }
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Replaces display and inline math formulas with opaque placeholders appended to {@code formulas}.
     * Display math is extracted before inline math so {@code $$...$$} does not get split by the inline pattern.
     *
     * @param markdown the markdown to process
     * @param formulas the list to append extracted formulas to; placeholders in the return value are
     *                     indexed by position in this list
     * @return markdown with formulas replaced by placeholders
     */
    public static String extract(String markdown, List<Formula> formulas) {
        String result = extractDisplayMath(markdown, formulas);
        result = INLINE_MATH_PATTERN.matcher(result).replaceAll(match -> {
            int index = formulas.size();
            formulas.add(new Formula(match.group(1), false));
            return Matcher.quoteReplacement(PLACEHOLDER_PREFIX + index + PLACEHOLDER_SUFFIX);
        });
        return result;
    }

    /**
     * Replaces every line that consists of a single {@code $$...$$} formula with a placeholder.
     * <p>
     * Scanned rather than matched with a regular expression. Any pattern for this shape needs a repetition over the body,
     * and Java's engine recurses once per repetition: an earlier {@code ^\$\$([^$]*(?:\$(?!\$)[^$]*)*)\$\$$} raised a
     * {@link StackOverflowError} on a line holding a few thousand single dollar signs - not only on crafted input, but on a
     * genuine formula that escapes that many dollars. A scan has no such limit and is linear in the length of the line.
     * <p>
     * Lines are separated the way CommonMark separates them - LF, CRLF or a bare CR - and each separator is copied back as
     * it was written, so what counts as "a line of its own" here is what the renderer downstream will also treat as one.
     *
     * @param markdown the markdown to scan
     * @param formulas the list to append extracted formulas to
     * @return the markdown with display formulas replaced by placeholders
     */
    private static String extractDisplayMath(String markdown, List<Formula> formulas) {
        StringBuilder result = new StringBuilder(markdown.length());
        int lineStart = 0;
        while (true) {
            int terminator = indexOfLineTerminator(markdown, lineStart);
            String line = markdown.substring(lineStart, terminator < 0 ? markdown.length() : terminator);
            int[] span = findDoubleDollarSpan(line);
            // Only a line that is nothing but the formula is display math; anything else stays for the inline pattern.
            String latex = span != null && span[0] == 0 && span[1] == line.length() ? line.substring(2, line.length() - 2).trim() : "";
            if (latex.isEmpty()) {
                // Covers both "not display math" and an empty body such as `$$$$`, which is left exactly as written.
                result.append(line);
            }
            else {
                formulas.add(new Formula(latex, true));
                result.append(PLACEHOLDER_PREFIX).append(formulas.size() - 1).append(PLACEHOLDER_SUFFIX);
            }
            if (terminator < 0) {
                return result.toString();
            }
            boolean isCrLf = markdown.charAt(terminator) == '\r' && terminator + 1 < markdown.length() && markdown.charAt(terminator + 1) == '\n';
            result.append(markdown, terminator, terminator + (isCrLf ? 2 : 1));
            lineStart = terminator + (isCrLf ? 2 : 1);
        }
    }

    /**
     * @param text the text to scan
     * @param from where to start scanning
     * @return the index of the next CR or LF at or after {@code from}, or {@code -1} when the rest holds neither
     */
    private static int indexOfLineTerminator(String text, int from) {
        for (int position = from; position < text.length(); position++) {
            char character = text.charAt(position);
            if (character == '\n' || character == '\r') {
                return position;
            }
        }
        return -1;
    }

    /**
     * Finds the first {@code $$...$$} span in a single line. The body may hold single dollar signs, as escaped currency in
     * {@code $$\text{Price: \$5}$$} does; the span ends at the first following {@code $$}.
     *
     * @param line the line to scan, without its line terminator
     * @return the index of the opening {@code $$} and the index just past the closing one, or {@code null} when the line
     *         holds no closed {@code $$...$$} span
     */
    private static int[] findDoubleDollarSpan(String line) {
        int open = line.indexOf("$$");
        if (open < 0) {
            return null;
        }
        int close = line.indexOf("$$", open + 2);
        return close < 0 ? null : new int[] { open, close + 2 };
    }

    /**
     * Whether a line uses the inline convention, i.e. holds a {@code $$...$$} formula that shares its line with other
     * text.
     * <p>
     * A bare carriage return ends the text that can surround a formula, even though the line was split on LF alone. The
     * client's {@code /.+\$\$[^$]+\$\$|\$\$[^$]+\$\$.+/} says the same with {@code .}, which matches no line terminator in
     * either language, so {@code before\r$$x^2$$\rafter} is not the inline convention for it either. It matters beyond
     * matching the client: {@link #extractDisplayMath} ends a line on a bare CR, so rewriting such a formula to single
     * dollars here would turn display math into inline math before the extraction ever saw it.
     * <p>
     * A match anywhere in the line rewrites the whole line, including across a carriage return, because that is what the
     * client does with the line it tested.
     *
     * @param line the line to inspect, without a trailing carriage return
     * @return true if any carriage-return-delimited segment of the line uses the inline convention
     */
    private static boolean usesInlineConvention(String line) {
        for (String segment : line.split("\r", -1)) {
            int[] span = findInlineConventionSpan(segment);
            // The inline convention means the formula shares its segment with other text, i.e. the span does not cover it.
            if (span != null && (span[0] > 0 || span[1] < segment.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first {@code $$...$$} span whose body holds at least one character and no dollar sign - the shape that the
     * inline convention of {@link #applyCompatibility} rewrites to single dollars.
     * <p>
     * A body with a dollar sign is deliberately excluded, as it was by the pattern this replaces. Rewriting such a formula
     * to {@code $...$} would hand the inline pattern a body it cannot represent, and the formula would come out cut off at
     * the inner dollar - worse than leaving the line as the author wrote it.
     *
     * @param line the line to scan, without its line terminator
     * @return the index of the opening {@code $$} and the index just past the closing one, or {@code null} when the line
     *         holds no such span
     */
    private static int[] findInlineConventionSpan(String line) {
        int open = line.indexOf("$$");
        while (open >= 0) {
            int bodyEnd = open + 2;
            while (bodyEnd < line.length() && line.charAt(bodyEnd) != '$') {
                bodyEnd++;
            }
            boolean closed = bodyEnd + 1 < line.length() && line.charAt(bodyEnd) == '$' && line.charAt(bodyEnd + 1) == '$';
            if (bodyEnd > open + 2 && closed) {
                return new int[] { open, bodyEnd + 2 };
            }
            // Try the next opening delimiter, the way the engine retried each start position.
            open = line.indexOf("$$", open + 1);
        }
        return null;
    }

    /**
     * @param line a line as split on {@code \n}
     * @return the line without a trailing carriage return, so that a CRLF document is treated like an LF one
     */
    private static String withoutCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /**
     * Replaces the NUL placeholders produced by {@link #extract} with a token-guarded marker span (see
     * {@link #MARKER_PREFIX}). The marker survives CommonMark and jsoup; the MathML is injected only afterwards by
     * {@link #injectMathml}.
     *
     * @param html     the HTML containing NUL placeholders
     * @param formulas the list of formulas indexed by placeholder position
     * @param token    the per-render token that makes the marker unforgeable
     * @return the HTML with placeholders replaced by marker spans
     */
    public static String restore(String html, List<Formula> formulas, String token) {
        return IndexedPlaceholders.replaceAll(html, PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX, formulas.size(), index -> MARKER_PREFIX + token + "-" + index + MARKER_SUFFIX);
    }

    /**
     * Replaces each marker span with its sanitized MathML, or with the escaped LaTeX source when
     * {@link LatexToMathmlConverter} could not convert it (unsupported command, limit exceeded, disallowed element).
     * A marker not carrying this render's token was written by the author, not by {@link #restore}, and is left as the
     * inert empty span it is. Runs after jsoup and independent of {@code includeJs}.
     *
     * @param html     the sanitized HTML containing marker spans
     * @param formulas the list of formulas indexed by marker position
     * @param token    the per-render token the markers carry
     * @return the HTML with every marker replaced by MathML or escaped source
     */
    public static String injectMathml(String html, List<Formula> formulas, String token) {
        // A statement with an unreasonable number of formulas renders them all as source rather than paying for that
        // many conversions; it bounds the per-request cost and only ever triggers on pathological input.
        boolean tooManyToConvert = formulas.size() > MAX_FORMULAS_TO_CONVERT;
        return IndexedPlaceholders.replaceAll(html, MARKER_PREFIX + token + "-", MARKER_SUFFIX, formulas.size(), index -> {
            Formula formula = formulas.get(index);
            Optional<String> mathml = tooManyToConvert ? Optional.empty() : LatexToMathmlConverter.toMathml(formula.latex(), formula.displayMode());
            return mathml.orElseGet(() -> sourceFallback(formula));
        });
    }

    /**
     * The readable fallback for a formula that could not be converted: its source between delimiters, HTML-escaped.
     * Attribute escaping is a valid superset of text escaping, so {@link HtmlEscaper#escapeAttribute} is reused.
     */
    private static String sourceFallback(Formula formula) {
        String delimiter = formula.displayMode() ? "$$" : "$";
        return "<span class=\"artemis-formula-source\">" + HtmlEscaper.escapeAttribute(delimiter + formula.latex() + delimiter) + "</span>";
    }
}
