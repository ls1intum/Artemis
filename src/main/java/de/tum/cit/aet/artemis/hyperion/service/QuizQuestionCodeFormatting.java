package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Sets source code that a generated question carries as plain text in Markdown code, so that it renders as code. A text that already
 * contains a backtick is left as it is.
 * <p>
 * A line counts as code when it ends like a statement or a block, with {@code ;}, <code>{</code> or <code>}</code>, or when it starts
 * with a keyword of a C-family language and carries brackets, braces, a semicolon or an assignment. A line that reads as prose, four
 * or more tokens of which most are plain words and none is an operator, and a line that holds a Markdown list item, table row, heading,
 * quote or LaTeX never count as code.
 */
final class QuizQuestionCodeFormatting {

    private static final Pattern STATEMENT_END = Pattern.compile("[;{}]\\s*(//.*)?$");

    /** Words that open a statement or declaration in a C-family language. */
    private static final Set<String> KEYWORDS = Set.of("public", "private", "protected", "static", "final", "abstract", "void", "int", "long", "short", "byte", "double", "float",
            "boolean", "char", "String", "class", "interface", "enum", "record", "return", "for", "while", "do", "if", "else", "switch", "case", "break", "continue", "try",
            "catch", "finally", "throw", "throws", "new", "import", "package", "var", "def");

    private static final Pattern LEADING_WORD = Pattern.compile("^\\w+");

    private static final Pattern CODE_PUNCTUATION = Pattern.compile("[(){}\\[\\];=]");

    private static final Pattern MARKDOWN_LINE_START = Pattern.compile("^(?:[-*+>]\\s|#+\\s|\\||\\d+[.)]\\s)");

    private static final Pattern LATEX = Pattern.compile("\\\\[(\\[]|\\$");

    private static final Pattern PLAIN_WORD = Pattern.compile("^\\p{L}+[,.?!:;]?$");

    /** Tokens that only appear between operands in code. */
    private static final Set<String> OPERATORS = Set.of("=", "==", "!=", "<=", ">=", "<", ">", "+", "-", "*", "/", "%", "&&", "||", "+=", "-=", "*=", "/=", "->", "=>", "?", "{",
            "}", "(", ")");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Tokens from which on a line is judged by its share of plain words. */
    private static final int MIN_PROSE_TOKENS = 4;

    /** Share of plain words from which on a line reads as prose. */
    private static final double MIN_PROSE_WORD_SHARE = 0.6;

    private static final String FENCE = "```";

    private QuizQuestionCodeFormatting() {
    }

    /**
     * Wraps code in Markdown code: a single-line text that is code in backticks, and every run of code lines in a multi-line text in a
     * fenced block.
     *
     * @param text the text of a question, an option or an explanation; may be {@code null}
     * @return the text with its code in Markdown code, or the text unchanged when it contains a backtick or no code
     */
    static @Nullable String formatCode(@Nullable String text) {
        if (text == null || text.indexOf('`') >= 0) {
            return text;
        }
        List<String> lines = List.of(text.split("\n", -1));
        if (lines.size() == 1) {
            return isCodeLine(text) ? "`" + text.strip() + "`" : text;
        }
        List<String> result = new ArrayList<>();
        List<String> blankLines = new ArrayList<>();
        boolean inCode = false;
        for (String line : lines) {
            if (line.isBlank()) {
                blankLines.add(line);
                continue;
            }
            boolean code = isCodeLine(line);
            if (code && !inCode) {
                result.addAll(blankLines);
                result.add(FENCE);
            }
            else if (!code && inCode) {
                result.add(FENCE);
                result.addAll(blankLines);
            }
            else {
                result.addAll(blankLines);
            }
            blankLines.clear();
            inCode = code;
            result.add(line);
        }
        if (inCode) {
            result.add(FENCE);
        }
        result.addAll(blankLines);
        return String.join("\n", result);
    }

    private static boolean isCodeLine(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty() || MARKDOWN_LINE_START.matcher(stripped).find() || LATEX.matcher(stripped).find() || readsAsProse(stripped)) {
            return false;
        }
        return STATEMENT_END.matcher(stripped).find() || (startsWithKeyword(stripped) && CODE_PUNCTUATION.matcher(stripped).find());
    }

    private static boolean startsWithKeyword(String line) {
        if (line.startsWith("System.out")) {
            return true;
        }
        var leadingWord = LEADING_WORD.matcher(line);
        return leadingWord.find() && KEYWORDS.contains(leadingWord.group());
    }

    private static boolean readsAsProse(String line) {
        String[] tokens = WHITESPACE.split(line);
        if (tokens.length < MIN_PROSE_TOKENS) {
            return false;
        }
        long plainWords = 0;
        for (String token : tokens) {
            if (OPERATORS.contains(token)) {
                return false;
            }
            if (PLAIN_WORD.matcher(token).matches()) {
                plainWords++;
            }
        }
        return (double) plainWords / tokens.length > MIN_PROSE_WORD_SHARE;
    }
}
