package de.tum.cit.aet.artemis.programming.service.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds and rewrites the Ares {@code @Policy} annotations of a Java source file.
 * <p>
 * The source is first masked: comments and the contents of string, character and text block literals are replaced by spaces, keeping every offset. All
 * searches run on the masked source, so an annotation or import that only appears in a comment or a string is never touched, and the parentheses and commas
 * of a literal never end an annotation or split its arguments. Argument names are read from the masked source as well, so a comment before a name is no
 * obstacle. The rewritten text is taken from the original source, so every argument keeps its comments; the values that are evaluated, like the
 * {@code activated} flag and the policy path, are read from the source without its comments.
 * <p>
 * The fully qualified {@code @de.tum.cit.ase.ares.api.Policy} always refers to Ares. The simple {@code @Policy} refers to Ares if the file imports it, either
 * directly or with {@code import de.tum.cit.ase.ares.api.*;} while no other {@code Policy} type is imported by name.
 */
final class AresPolicyAnnotations {

    private static final String ARES_POLICY = "de.tum.cit.ase.ares.api.Policy";

    private static final Pattern POLICY_ANNOTATION_NAME = Pattern.compile("@(de\\.tum\\.cit\\.ase\\.ares\\.api\\.)?Policy\\b");

    private static final Pattern SINGLE_TYPE_IMPORT = Pattern.compile("\\bimport\\s+([\\w.]+)\\s*;");

    private static final Pattern ARES_WILDCARD_IMPORT = Pattern.compile("\\bimport\\s+de\\.tum\\.cit\\.ase\\.ares\\.api\\.\\*\\s*;");

    /** A named annotation argument such as {@code activated = false}, matched on the masked source. */
    private static final Pattern NAMED_ARGUMENT = Pattern.compile("^\\s*([A-Za-z_]\\w*)\\s*=(?!=)\\s*(.*?)\\s*$", Pattern.DOTALL);

    private static final String ACTIVATED_ARGUMENT = "activated";

    private static final String VALUE_ARGUMENT = "value";

    /**
     * One argument of an annotation.
     *
     * @param named      whether the argument is written with its name; a single unnamed argument is the {@code value}
     * @param text       the argument as written, including its comments
     * @param expression the value expression without comments, for evaluating it
     */
    record Argument(boolean named, String text, String expression) {

        private String render() {
            return named ? text : VALUE_ARGUMENT + " = " + text;
        }
    }

    /**
     * One Ares {@code @Policy} annotation in a source file.
     *
     * @param start     offset of the {@code @}
     * @param end       offset after the annotation, including its argument list
     * @param name      the annotation as written, e.g. {@code @Policy}
     * @param arguments the arguments by name, in their order
     */
    record Annotation(int start, int end, String name, Map<String, Argument> arguments) {

        /**
         * @return whether the annotation enforces its policy, i.e. its {@code activated} argument is not the literal {@code false}, also when it is written
         *         in parentheses like {@code activated = (false)}
         */
        boolean isActivated() {
            Argument activated = arguments.get(ACTIVATED_ARGUMENT);
            return activated == null || !"false".equals(withoutOuterParentheses(activated.expression()));
        }

        /**
         * Ares resolves the policy path relative to the root of the tests repository, so the path is returned in its normalized form relative to that
         * root: {@code ./SecurityPolicy.yaml} and {@code test/../SecurityPolicy.yaml} both are {@code SecurityPolicy.yaml}.
         *
         * @return the policy file the annotation points to, if its {@code value} is a plain string literal; empty if it has no {@code value}, in which
         *         case Ares applies its strict default policy, or if the value is an expression this class does not evaluate
         */
        Optional<String> policyPath() {
            Argument value = arguments.get(VALUE_ARGUMENT);
            if (value == null) {
                return Optional.empty();
            }
            String literal = withoutOuterParentheses(value.expression());
            boolean plainString = literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"") && !literal.startsWith("\"\"\"") && literal.indexOf('\\') < 0
                    && literal.indexOf('"', 1) == literal.length() - 1;
            return plainString ? Optional.of(normalizePath(literal.substring(1, literal.length() - 1))) : Optional.empty();
        }
    }

    /**
     * Removes the parentheses that enclose a whole expression, like {@code ((false))}, but not those of {@code (a) + (b)}.
     */
    private static String withoutOuterParentheses(String expression) {
        String result = expression.strip();
        while (result.startsWith("(") && matchingParenthesis(mask(result), 0) == result.length() - 1) {
            result = result.substring(1, result.length() - 1).strip();
        }
        return result;
    }

    /**
     * @return the path in normalized form with forward slashes, e.g. {@code SecurityPolicy.yaml} for {@code ./SecurityPolicy.yaml}; the path as written
     *         if it is not a valid path
     */
    private static String normalizePath(String path) {
        try {
            String normalized = Path.of(path).normalize().toString().replace('\\', '/');
            return normalized.isEmpty() ? path : normalized;
        }
        catch (InvalidPathException e) {
            return path;
        }
    }

    private AresPolicyAnnotations() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Switches every Ares {@code @Policy} annotation of the source on or off. Only the {@code activated} argument changes; the policy path and every other
     * argument are kept, so switching an annotation off and on again restores it exactly. An annotation that already has the requested state is not touched.
     *
     * @param source the content of a Java file
     * @param active whether the annotations should enforce their policy
     * @return the rewritten source
     */
    static String setActive(String source, boolean active) {
        StringBuilder result = new StringBuilder(source);
        List<Annotation> annotations = find(source);
        // Replace from the end, so the offsets of the annotations before stay valid.
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation annotation = annotations.get(i);
            if (annotation.isActivated() != active) {
                result.replace(annotation.start(), annotation.end(), render(annotation, active));
            }
        }
        return result.toString();
    }

    private static String render(Annotation annotation, boolean active) {
        Map<String, Argument> arguments = new LinkedHashMap<>(annotation.arguments());
        if (active) {
            arguments.remove(ACTIVATED_ARGUMENT);
        }
        else {
            arguments.put(ACTIVATED_ARGUMENT, new Argument(true, ACTIVATED_ARGUMENT + " = false", "false"));
        }
        if (arguments.isEmpty()) {
            return annotation.name();
        }
        StringJoiner rendered = new StringJoiner(", ", annotation.name() + "(", ")");
        arguments.values().forEach(argument -> rendered.add(argument.render()));
        return rendered.toString();
    }

    /**
     * @param source the content of a Java file
     * @return the Ares {@code @Policy} annotations of the source, in their order
     */
    static List<Annotation> find(String source) {
        String masked = mask(source);
        String withoutComments = maskComments(source);
        boolean simpleNameIsAres = simpleNameRefersToAres(masked);
        List<Annotation> annotations = new ArrayList<>();
        Matcher names = POLICY_ANNOTATION_NAME.matcher(masked);
        while (names.find()) {
            boolean qualified = names.group(1) != null;
            if (!qualified && (!simpleNameIsAres || isPartOfQualifiedName(masked, names.start()))) {
                continue;
            }
            int end = names.end();
            int open = skipWhitespace(masked, end);
            Map<String, Argument> arguments = new LinkedHashMap<>();
            if (open < masked.length() && masked.charAt(open) == '(') {
                int close = matchingParenthesis(masked, open);
                if (close < 0) {
                    // An incomplete annotation, e.g. in a file that does not compile: leave it alone.
                    continue;
                }
                arguments = parseArguments(new Sources(source, withoutComments, masked), open + 1, close);
                end = close + 1;
            }
            annotations.add(new Annotation(names.start(), end, source.substring(names.start(), names.end()), arguments));
        }
        return annotations;
    }

    /**
     * @return whether the character before the {@code @} makes it part of a longer name, which then is not the simple Ares annotation
     */
    private static boolean isPartOfQualifiedName(String masked, int at) {
        return at > 0 && (Character.isJavaIdentifierPart(masked.charAt(at - 1)) || masked.charAt(at - 1) == '.');
    }

    private static boolean simpleNameRefersToAres(String masked) {
        boolean importsAresPolicy = false;
        boolean importsOtherPolicy = false;
        Matcher imports = SINGLE_TYPE_IMPORT.matcher(masked);
        while (imports.find()) {
            String imported = imports.group(1);
            if (ARES_POLICY.equals(imported)) {
                importsAresPolicy = true;
            }
            else if (imported.endsWith(".Policy")) {
                importsOtherPolicy = true;
            }
        }
        return importsAresPolicy || (!importsOtherPolicy && ARES_WILDCARD_IMPORT.matcher(masked).find());
    }

    private static int skipWhitespace(String text, int from) {
        int index = from;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * @return the offset of the parenthesis that closes the one at {@code open}, or -1 if there is none
     */
    private static int matchingParenthesis(String masked, int open) {
        int depth = 0;
        for (int index = open; index < masked.length(); index++) {
            char character = masked.charAt(index);
            if (character == '(') {
                depth++;
            }
            else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * The same source three times, with equal offsets: as written, without comments, and with comments and literal contents masked.
     */
    private record Sources(String original, String withoutComments, String masked) {
    }

    /**
     * Splits the argument list between {@code from} and {@code to} at its top-level commas and returns each argument by name. A single unnamed argument is
     * the {@code value}, as in Java.
     */
    private static Map<String, Argument> parseArguments(Sources sources, int from, int to) {
        String masked = sources.masked();
        Map<String, Argument> arguments = new LinkedHashMap<>();
        int depth = 0;
        int argumentStart = from;
        for (int index = from; index <= to; index++) {
            char character = index < to ? masked.charAt(index) : ',';
            if (character == '(' || character == '{' || character == '[') {
                depth++;
            }
            else if (character == ')' || character == '}' || character == ']') {
                depth--;
            }
            else if (character == ',' && depth == 0) {
                addArgument(arguments, sources, argumentStart, index);
                argumentStart = index + 1;
            }
        }
        return arguments;
    }

    /**
     * Adds the argument between {@code from} and {@code to}. Its name is read from the masked source, so a comment before it does not hide it. Its text
     * keeps every comment, except a line comment at its end, which would comment out an argument that is appended behind it.
     */
    private static void addArgument(Map<String, Argument> arguments, Sources sources, int from, int to) {
        String original = sources.original();
        String withoutComments = sources.withoutComments();
        int codeEnd = lastNonWhitespace(withoutComments, from, to);
        if (codeEnd == from) {
            // Only whitespace and comments, e.g. after a trailing comma.
            return;
        }
        int textEnd = lastNonWhitespace(original, from, to);
        if (original.substring(codeEnd, textEnd).contains("//")) {
            textEnd = codeEnd;
        }
        String text = original.substring(skipWhitespace(original, from), textEnd);
        Matcher named = NAMED_ARGUMENT.matcher(sources.masked().substring(from, to));
        if (named.matches()) {
            String expression = withoutComments.substring(from + named.start(2), from + named.end(2)).strip();
            arguments.put(named.group(1), new Argument(true, text, expression));
        }
        else {
            arguments.put(VALUE_ARGUMENT, new Argument(false, text, withoutComments.substring(from, to).strip()));
        }
    }

    /**
     * @return the offset after the last character between {@code from} and {@code to} that is not whitespace, or {@code from} if there is none
     */
    private static int lastNonWhitespace(String text, int from, int to) {
        int end = to;
        while (end > from && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    /**
     * Replaces comments and the contents of string, character and text block literals by spaces. Line breaks and the quotes of literals are kept, so
     * every offset and line of the result matches the source.
     *
     * @param source the content of a Java file
     * @return the masked source
     */
    static String mask(String source) {
        return mask(source, true);
    }

    /**
     * Replaces only the comments by spaces, keeping every offset, so the values of literals can still be read.
     */
    private static String maskComments(String source) {
        return mask(source, false);
    }

    private static String mask(String source, boolean maskLiterals) {
        StringBuilder masked = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (source.startsWith("//", index)) {
                index = maskUntil(source, masked, index, source.indexOf('\n', index), 0);
            }
            else if (source.startsWith("/*", index)) {
                int close = source.indexOf("*/", index + 2);
                index = maskUntil(source, masked, index, close < 0 ? -1 : close + 2, 0);
            }
            else if (source.startsWith("\"\"\"", index)) {
                masked.append("\"\"\"");
                index = maskLiteral(source, masked, index + 3, "\"\"\"", maskLiterals);
            }
            else if (character == '"' || character == '\'') {
                masked.append(character);
                index = maskLiteral(source, masked, index + 1, String.valueOf(character), maskLiterals);
            }
            else {
                masked.append(character);
                index++;
            }
        }
        return masked.toString();
    }

    /**
     * Masks the source from {@code from} up to {@code to} (exclusive, or the end of the source if negative) and appends {@code keep} characters unmasked.
     */
    private static int maskUntil(String source, StringBuilder masked, int from, int to, int keep) {
        int end = to < 0 ? source.length() : to;
        for (int index = from; index < end; index++) {
            masked.append(source.charAt(index) == '\n' ? '\n' : ' ');
        }
        return end + keep;
    }

    /**
     * Appends the content of a literal that started before {@code from}, masked or as written, and its closing delimiter.
     *
     * @return the offset after the closing delimiter
     */
    private static int maskLiteral(String source, StringBuilder masked, int from, String delimiter, boolean maskContent) {
        int index = from;
        while (index < source.length()) {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                appendLiteralCharacter(masked, source.charAt(index), maskContent);
                appendLiteralCharacter(masked, source.charAt(index + 1), maskContent);
                index += 2;
            }
            else if (source.startsWith(delimiter, index)) {
                masked.append(delimiter);
                return index + delimiter.length();
            }
            else {
                appendLiteralCharacter(masked, source.charAt(index), maskContent);
                index++;
            }
        }
        return index;
    }

    private static void appendLiteralCharacter(StringBuilder masked, char character, boolean maskContent) {
        masked.append(!maskContent || character == '\n' ? character : ' ');
    }
}
