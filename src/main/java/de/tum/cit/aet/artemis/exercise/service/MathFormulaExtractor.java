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
     * Matches a {@code $$...$$} formula. Whether the line uses the inline convention is decided from the match position
     * rather than by a pattern.
     * <p>
     * The previous pattern anchored a leading or trailing {@code .+} against the literal delimiters, which backtracks
     * quadratically: a single 100 KB line took eight seconds without any {@code $} in it and twenty-eight seconds when
     * filled with them. Matching only the formula and comparing bounds is linear.
     */
    private static final Pattern DOUBLE_DOLLAR_FORMULA = Pattern.compile("\\$\\$[^$]+\\$\\$");

    /**
     * Display math: {@code $$...$$} on its own line.
     * <p>
     * The body is written as an unrolled loop - a run of non-{@code $} characters, then any number of "single {@code $}
     * followed by more non-{@code $}" groups - rather than as {@code (?:[^$]|\$(?!\$))+}. That earlier shape put an
     * alternation inside a quantifier, which recurses once per character: an unclosed {@code $$} followed by 100 KB of text
     * raised a {@link StackOverflowError}. The unrolled form consumes at least one character per iteration, so it does not.
     * <p>
     * It permits an empty body, which the previous pattern did not; {@link #extract} skips those explicitly.
     */
    private static final Pattern DISPLAY_MATH_PATTERN = Pattern.compile("^\\$\\$([^$]*(?:\\$(?!\\$)[^$]*)*)\\$\\$$", Pattern.MULTILINE);

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
            Matcher formula = DOUBLE_DOLLAR_FORMULA.matcher(line);
            // The inline convention means the formula shares its line with other text, i.e. the match does not span it.
            if (formula.find() && (formula.start() > 0 || formula.end() < line.length())) {
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
            String latex = match.group(1).trim();
            if (latex.isEmpty()) {
                // The pattern allows an empty body so that it stays linear; an empty formula is left as written.
                return Matcher.quoteReplacement(match.group());
            }
            int index = formulas.size();
            formulas.add(new Formula(latex, true));
            return Matcher.quoteReplacement(PLACEHOLDER_PREFIX + index + PLACEHOLDER_SUFFIX);
        });
        result = INLINE_MATH_PATTERN.matcher(result).replaceAll(match -> {
            int index = formulas.size();
            formulas.add(new Formula(match.group(1), false));
            return Matcher.quoteReplacement(PLACEHOLDER_PREFIX + index + PLACEHOLDER_SUFFIX);
        });
        return result;
    }

    /** Matches a rendered but still empty formula placeholder, capturing its escaped source and its remaining attributes. */
    private static final Pattern EMPTY_FORMULA_SPAN = Pattern.compile("<span class=\"katex-formula\" data-formula=\"([^\"]*)\"([^>]*)></span>");

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
            String escapedSource = matcher.group(1);
            String otherAttributes = matcher.group(2);
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement("<span class=\"katex-formula\" data-formula=\"" + escapedSource + "\"" + otherAttributes + ">" + escapedSource + "</span>"));
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
