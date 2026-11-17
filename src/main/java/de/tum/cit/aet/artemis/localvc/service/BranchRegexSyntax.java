package de.tum.cit.aet.artemis.localvc.service;

import org.jspecify.annotations.Nullable;

/**
 * Decides whether a regular expression that an instructor wrote for the names of branches only uses a small, documented subset of the Java syntax.
 * <p>
 * The expression is matched by backtracking. Within the supported subset, every step of a loop or of backtracking either reads a character of the name or is bounded by a small
 * number, so the time budget of {@link TimeBoundedRegexMatcher}, which is checked whenever a character is read, applies to the whole match. The subset achieves this as follows:
 * <ul>
 * <li>Anchors are zero-width. They can only be the first or the last token of an alternative, and they cannot be repeated.</li>
 * <li>Every alternative inside a group must consume at least one character. Adjacent groups cannot multiply successful zero-width paths without checking the deadline.</li>
 * <li>A repeated group is a plain sequence of atoms. It contains no alternation, no quantifier and no anchor, so every iteration of the group reads at least one character.</li>
 * <li>An atom has at most one quantifier, and the counts are at most {@value #MAX_COUNT}.</li>
 * <li>Constructs that look around, refer back to groups, change flags or switch to another syntax are not part of the subset.</li>
 * </ul>
 * Anything that is not recognized is not supported, so a construct that is added to the syntax of Java later is not supported either.
 * <p>
 * Supported:
 * <ul>
 * <li>Literal characters, except the characters {@code \ ^ $ . | ? * + ( ) [ ] { }}. The closing brackets {@code ]} and <code>}</code> are also literals outside of classes.</li>
 * <li>An escaped punctuation character, one of {@code \ . / - _ [ ] ( ) { } | ? * + ^ $}, and the escapes {@code \d \w \s \D \W \S}.</li>
 * <li>The wildcard {@code .}.</li>
 * <li>Classes such as {@code [a-z0-9_-]} and {@code [^/]}, with literals, ranges and the escapes above, but no nested class and no intersection.</li>
 * <li>Groups {@code (...)} and {@code (?:...)} with a nesting depth of at most {@value #MAX_GROUP_DEPTH}, and alternatives with {@code |}. No alternative and no group is
 * empty.</li>
 * <li>{@code ^} as the first token and {@code $} as the last token of an alternative.</li>
 * <li>The quantifiers {@code *}, {@code +}, {@code ?}, <code>{n}</code>, <code>{n,}</code> and <code>{n,m}</code> after an atom.</li>
 * </ul>
 */
public final class BranchRegexSyntax {

    /** The longest expression that is supported. */
    public static final int MAX_LENGTH = 128;

    /** The largest count of a quantifier such as {@code {n,m}}. */
    static final int MAX_COUNT = 1000;

    /** The deepest nesting of groups. */
    static final int MAX_GROUP_DEPTH = 5;

    /** A description of the supported syntax for messages. */
    public static final String DESCRIPTION = "Supported are literal characters, escaped punctuation, the escapes \\d \\w \\s \\D \\W \\S, '.', classes such as [a-z0-9-], groups (...) and (?:...) "
            + "with alternatives '|', the anchors '^' at the start and '$' at the end of an alternative, and the quantifiers * + ? {n} {n,} {n,m} (counts up to " + MAX_COUNT
            + "), with one quantifier per element, at least one required character per group alternative, and without alternatives, quantifiers or anchors in a repeated group.";

    private static final String METACHARACTERS = "\\^$.|?*+()[]{}";

    private static final String ESCAPABLE_PUNCTUATION = "\\./-_[](){}|?*+^$";

    private static final String CLASS_ESCAPES = "dwsDWS";

    private final String regex;

    private int position;

    private BranchRegexSyntax(String regex) {
        this.regex = regex;
    }

    /**
     * Whether an expression only uses the supported subset.
     *
     * @param regex the expression, may be null
     * @return true if the expression is supported
     */
    public static boolean isSupported(@Nullable String regex) {
        if (regex == null || regex.isEmpty() || regex.length() > MAX_LENGTH) {
            return false;
        }
        BranchRegexSyntax parser = new BranchRegexSyntax(regex);
        try {
            parser.parseAlternatives(0);
            // the end of the expression has to be reached, a closing parenthesis without an opening one stops the parser earlier
            return parser.position == regex.length();
        }
        catch (UnsupportedSyntaxException e) {
            return false;
        }
    }

    private static final class UnsupportedSyntaxException extends RuntimeException {

        UnsupportedSyntaxException() {
            super("unsupported", null, false, false);
        }
    }

    private static UnsupportedSyntaxException unsupported() {
        return new UnsupportedSyntaxException();
    }

    private boolean atEnd() {
        return position >= regex.length();
    }

    private char current() {
        return regex.charAt(position);
    }

    /** Parses alternatives up to a closing parenthesis or the end. Returns whether the alternatives are plain: one alternative without quantifier and anchor. */
    private boolean parseAlternatives(int depth) {
        boolean plain = true;
        while (true) {
            plain &= parseSequence(depth);
            if (!atEnd() && current() == '|') {
                position++;
                plain = false;
            }
            else {
                return plain;
            }
        }
    }

    private boolean parseSequence(int depth) {
        boolean plain = true;
        boolean consumes = false;
        int items = 0;
        while (!atEnd() && current() != '|' && current() != ')') {
            char c = current();
            if (c == '^') {
                if (items > 0) {
                    throw unsupported();
                }
                position++;
                plain = false;
            }
            else if (c == '$') {
                position++;
                if (!atEnd() && current() != '|' && current() != ')') {
                    throw unsupported();
                }
                plain = false;
            }
            else {
                boolean atomIsPlainGroup = parseAtom(depth);
                int minimum = parseQuantifier();
                if (minimum >= 0) {
                    if (!atomIsPlainGroup) {
                        throw unsupported();
                    }
                    plain = false;
                }
                // A parsed atom consumes on success: nested groups have already passed this same check.
                // Optional atoms cannot establish that an alternative must consume input.
                consumes |= minimum != 0;
                plain &= atomIsPlainGroup;
            }
            items++;
        }
        if (items == 0 || depth > 0 && !consumes) {
            throw unsupported();
        }
        return plain;
    }

    /**
     * Parses one atom. Returns whether it can be repeated: a literal, an escape, the wildcard, a class, or a group that is plain. A group that is not plain returns false, and a
     * quantifier after it is refused by the caller. A literal or a class returns true as well.
     */
    private boolean parseAtom(int depth) {
        char c = current();
        switch (c) {
            case '(' -> {
                return parseGroup(depth + 1);
            }
            case '[' -> parseClass();
            case '\\' -> parseEscape();
            case '.' -> position++;
            default -> {
                if (METACHARACTERS.indexOf(c) >= 0 && c != ']' && c != '}') {
                    throw unsupported();
                }
                // Java Pattern applies a quantifier to the whole code point, including a supplementary literal.
                position += Character.charCount(regex.codePointAt(position));
            }
        }
        return true;
    }

    private boolean parseGroup(int depth) {
        if (depth > MAX_GROUP_DEPTH) {
            throw unsupported();
        }
        position++;
        if (!atEnd() && current() == '?') {
            if (position + 1 >= regex.length() || regex.charAt(position + 1) != ':') {
                throw unsupported();
            }
            position += 2;
        }
        boolean plain = parseAlternatives(depth);
        if (atEnd() || current() != ')') {
            throw unsupported();
        }
        position++;
        return plain;
    }

    private void parseEscape() {
        position++;
        if (atEnd()) {
            throw unsupported();
        }
        char escaped = current();
        if (ESCAPABLE_PUNCTUATION.indexOf(escaped) < 0 && CLASS_ESCAPES.indexOf(escaped) < 0) {
            throw unsupported();
        }
        position++;
    }

    private void parseClass() {
        position++;
        if (!atEnd() && current() == '^') {
            position++;
        }
        int elements = 0;
        while (!atEnd() && current() != ']') {
            int low = readClassElement();
            if (low >= 0 && !atEnd() && current() == '-' && position + 1 < regex.length() && regex.charAt(position + 1) != ']') {
                position++;
                int high = readClassElement();
                if (high < low) {
                    throw unsupported();
                }
            }
            else if (low < 0 && !atEnd() && current() == '-' && position + 1 < regex.length() && regex.charAt(position + 1) != ']') {
                throw unsupported();
            }
            elements++;
        }
        if (atEnd() || elements == 0) {
            throw unsupported();
        }
        position++;
    }

    /** Reads one element of a class. Returns the character if it is a single character, and -1 for a class escape such as {@code \d}. */
    private int readClassElement() {
        char c = current();
        if (c == '[') {
            throw unsupported();
        }
        if (c == '&' && position + 1 < regex.length() && regex.charAt(position + 1) == '&') {
            throw unsupported();
        }
        int codePoint = regex.codePointAt(position);
        position += Character.charCount(codePoint);
        if (c != '\\') {
            return codePoint;
        }
        if (atEnd()) {
            throw unsupported();
        }
        char escaped = current();
        position++;
        if (CLASS_ESCAPES.indexOf(escaped) >= 0) {
            return -1;
        }
        if (ESCAPABLE_PUNCTUATION.indexOf(escaped) < 0) {
            throw unsupported();
        }
        return escaped;
    }

    /** Returns the minimum repetition count, or -1 without a quantifier. Lazy, possessive and repeated quantifiers are not supported. */
    private int parseQuantifier() {
        if (atEnd()) {
            return -1;
        }
        char c = current();
        int minimum;
        if (c == '*' || c == '+' || c == '?') {
            position++;
            minimum = c == '+' ? 1 : 0;
        }
        else if (c == '{') {
            minimum = parseCount();
        }
        else {
            return -1;
        }
        if (!atEnd() && (current() == '*' || current() == '+' || current() == '?' || current() == '{')) {
            throw unsupported();
        }
        return minimum;
    }

    private int parseCount() {
        position++;
        int lower = readNumber();
        int upper = lower;
        if (!atEnd() && current() == ',') {
            position++;
            upper = !atEnd() && current() == '}' ? Integer.MAX_VALUE : readNumber();
        }
        if (atEnd() || current() != '}' || lower > upper) {
            throw unsupported();
        }
        position++;
        return lower;
    }

    private int readNumber() {
        int start = position;
        int value = 0;
        while (!atEnd() && current() >= '0' && current() <= '9') {
            value = value * 10 + (current() - '0');
            position++;
            if (position - start > 4) {
                throw unsupported();
            }
        }
        if (position == start || value > MAX_COUNT) {
            throw unsupported();
        }
        return value;
    }
}
