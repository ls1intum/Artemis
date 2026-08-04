package de.tum.cit.aet.artemis.exercise.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts LaTeX math formulas from markdown and restores them as KaTeX-renderable placeholders.
 * <p>
 * Three responsibilities:
 * <ul>
 * <li>{@link #applyCompatibility(String)}: ports the Angular {@code FormulaCompatibilityPlugin} so authors
 * can write lines that mix inline math with surrounding text using {@code $$...$$}.</li>
 * <li>{@link #extract(String, List)}: replaces display and inline formulas with opaque placeholders so
 * downstream CommonMark rendering does not mangle them.</li>
 * <li>{@link #restore(String, List)}: replaces placeholders with {@code <span class="katex-formula">} elements
 * that the client-side KaTeX script picks up.</li>
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
     * the two renderers have to agree on what an author's markdown means. A trailing carriage return is ignored, which is
     * what the client's {@code .} does with it as well.
     *
     * @param markdown the markdown source
     * @return the markdown with formula-authoring quirks normalized
     */
    public static String applyCompatibility(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String content = withoutCarriageReturn(line);
            int[] span = findInlineConventionSpan(content);
            // The inline convention means the formula shares its line with other text, i.e. the span does not cover it.
            if (span != null && (span[0] > 0 || span[1] < content.length())) {
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
     * Matches a rendered but still empty formula placeholder, capturing its attributes as written.
     * <p>
     * Deliberately indifferent to attribute order and to whitespace between the tags: the element is written by
     * {@link #restore} but reaches this point through CommonMark and jsoup, and a serializer that reordered the attributes
     * or inserted whitespace would otherwise turn the fallback off silently rather than visibly.
     */
    private static final Pattern EMPTY_FORMULA_SPAN = Pattern.compile("<span([^>]*\\bclass=\"katex-formula\"[^>]*)>\\s*</span>");

    /** Reads the escaped formula source out of the attributes captured by {@link #EMPTY_FORMULA_SPAN}. */
    private static final Pattern DATA_FORMULA_ATTRIBUTE = Pattern.compile("\\bdata-formula=\"([^\"]*)\"");

    /**
     * Copies each formula's source into its placeholder as visible text, so that a document rendered without JavaScript
     * shows the formula source instead of nothing.
     * <p>
     * This deliberately runs <em>after</em> CommonMark rather than in {@link #restore}: inside the markdown the text would
     * be parsed as markdown, which strips the backslashes (turning {@code x\,dx} into {@code x,dx}) and reads underscores
     * as emphasis. The escaped attribute value is copied verbatim rather than unescaped and re-escaped, because HTML
     * attribute escaping is already valid as element text.
     * <p>
     * KaTeX replaces the element's content when it renders, so this text is only ever seen when no script runs.
     *
     * @param html the rendered HTML
     * @return the HTML with every formula placeholder carrying its own source as text
     */
    public static String fillFormulaSourceAsFallback(String html) {
        Matcher matcher = EMPTY_FORMULA_SPAN.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String attributes = matcher.group(1);
            Matcher source = DATA_FORMULA_ATTRIBUTE.matcher(attributes);
            if (!source.find()) {
                // No source to show. Left as it is, which the next appendReplacement copies over unchanged.
                continue;
            }
            String escapedSource = source.group(1);
            // The opening tag is reproduced as written, so nothing depends on how the attributes were spelled.
            matcher.appendReplacement(result, Matcher.quoteReplacement("<span" + attributes + ">" + escapedSource + "</span>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Replaces the placeholders produced by {@link #extract} with {@code <span class="katex-formula">}
     * elements that client-side KaTeX can render. The LaTeX source is HTML-escaped for attribute context.
     *
     * @param html     the HTML containing placeholders
     * @param formulas the list of formulas indexed by placeholder position
     * @return the HTML with placeholders replaced by KaTeX-ready span elements
     */
    public static String restore(String html, List<Formula> formulas) {
        return IndexedPlaceholders.replaceAll(html, PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX, formulas.size(), index -> {
            Formula formula = formulas.get(index);
            return "<span class=\"katex-formula\" data-formula=\"" + HtmlEscaper.escapeAttribute(formula.latex()) + "\" data-display-mode=\"" + formula.displayMode()
                    + "\"></span>";
        });
    }
}
