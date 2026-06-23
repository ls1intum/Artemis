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

    /** Matches a line where {@code $$...$$} appears with other text before or after (inline formula convention). */
    private static final Pattern INLINE_FORMULA_LINE = Pattern.compile(".+\\$\\$[^$]+\\$\\$|\\$\\$[^$]+\\$\\$.+");

    /**
     * Display math: {@code $$...$$} on its own line. The body is a sequence of non-{@code $} characters
     * or a single {@code $} not followed by another {@code $}. This explicit shape avoids the lazy
     * quantifier backtracking that a naive {@code [\s\S]+?} would cause.
     */
    private static final Pattern DISPLAY_MATH_PATTERN = Pattern.compile("^\\$\\$((?:[^$]|\\$(?!\\$))+)\\$\\$$", Pattern.MULTILINE);

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
     *
     * @param markdown the markdown source
     * @return the markdown with formula-authoring quirks normalized
     */
    public static String applyCompatibility(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (INLINE_FORMULA_LINE.matcher(line).find()) {
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
        String result = DISPLAY_MATH_PATTERN.matcher(markdown).replaceAll(match -> {
            int index = formulas.size();
            formulas.add(new Formula(match.group(1).trim(), true));
            return Matcher.quoteReplacement(PLACEHOLDER_PREFIX + index + PLACEHOLDER_SUFFIX);
        });
        result = INLINE_MATH_PATTERN.matcher(result).replaceAll(match -> {
            int index = formulas.size();
            formulas.add(new Formula(match.group(1), false));
            return Matcher.quoteReplacement(PLACEHOLDER_PREFIX + index + PLACEHOLDER_SUFFIX);
        });
        return result;
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
        String result = html;
        for (int i = 0; i < formulas.size(); i++) {
            Formula formula = formulas.get(i);
            String span = "<span class=\"katex-formula\" data-formula=\"" + HtmlEscaper.escapeAttribute(formula.latex()) + "\" data-display-mode=\"" + formula.displayMode()
                    + "\"></span>";
            result = result.replace(PLACEHOLDER_PREFIX + i + PLACEHOLDER_SUFFIX, span);
        }
        return result;
    }
}
