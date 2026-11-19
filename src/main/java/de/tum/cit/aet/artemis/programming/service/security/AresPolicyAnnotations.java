package de.tum.cit.aet.artemis.programming.service.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

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

    /**
     * The end of a Java identifier: unlike {@code \b}, it also rejects {@code $} and non-ASCII letters, so {@code @Policy$Tag} or {@code @PolicyÜ} is no
     * {@code @Policy}.
     */
    static final String END_OF_IDENTIFIER = "(?!\\p{javaJavaIdentifierPart})";

    /** The {@code @} of an annotation; Java allows whitespace and comments, which the masked source shows as whitespace, before its name. */
    static final String ANNOTATION_START = "@\\s*+";

    private static final Pattern POLICY_ANNOTATION_NAME = Pattern.compile(ANNOTATION_START + "(" + packagePrefix("de.tum.cit.ase.ares.api") + ")?Policy" + END_OF_IDENTIFIER);

    private static final Pattern SINGLE_TYPE_IMPORT = Pattern.compile("\\bimport\\s+([\\p{javaJavaIdentifierPart}.]+)\\s*;");

    /** A Java identifier; it may contain any Unicode letter, e.g. {@code SortingÜTest}, so it uses the identifier classes of {@link Character}. */
    private static final String JAVA_IDENTIFIER = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*+";

    /** A declaration of a type, of any kind and at any depth; the second group is its simple name. */
    static final Pattern TYPE_DECLARATION = Pattern.compile("(@\\s*interface|(?<![\\p{javaJavaIdentifierPart}.])(?:class|interface|enum|record))\\s+(" + JAVA_IDENTIFIER + ")");

    private static final Pattern ARES_WILDCARD_IMPORT = Pattern.compile("\\bimport\\s+de\\.tum\\.cit\\.ase\\.ares\\.api\\.\\*\\s*;");

    /** A named annotation argument such as {@code activated = false}, matched on the masked source. */
    private static final Pattern NAMED_ARGUMENT = Pattern.compile("^\\s*([A-Za-z_]\\w*)\\s*=(?!=)\\s*(.*?)\\s*$", Pattern.DOTALL);

    private static final String ACTIVATED_ARGUMENT = "activated";

    private static final String VALUE_ARGUMENT = "value";

    private static final String WITHIN_PATH_ARGUMENT = "withinPath";

    private static final String FALSE_LITERAL = "false";

    private static final String TEXT_BLOCK_DELIMITER = "\"\"\"";

    private static final Set<String> BOOLEAN_LITERALS = Set.of("true", FALSE_LITERAL);

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
     * @param start           offset of the {@code @}
     * @param end             offset after the annotation, including its argument list
     * @param name            the annotation as written, e.g. {@code @Policy}
     * @param beforeArguments the text between the name and the argument list as written, e.g. a comment; empty if there is no argument list
     * @param arguments       the arguments by name, in their order
     */
    record Annotation(int start, int end, String name, String beforeArguments, Map<String, Argument> arguments) {

        /**
         * Whether the arguments that decide the enforcement are written in a form this class evaluates. Only then do {@link #isActivated()} and
         * {@link #policyPath()} describe what Ares does: {@code value} must be absent or a plain string literal, and {@code activated} must be absent or the
         * literal {@code true} or {@code false}, each optionally in parentheses and with comments. Any other expression, such as
         * {@code "Security" + "Policy.yaml"}, {@code !true} or a constant, is valid Java that this class does not evaluate.
         *
         * @return whether the annotation can be evaluated
         */
        boolean isEvaluable() {
            Argument value = arguments.get(VALUE_ARGUMENT);
            Argument activated = arguments.get(ACTIVATED_ARGUMENT);
            return (value == null || plainStringContent(value).isPresent()) && (activated == null || BOOLEAN_LITERALS.contains(withoutOuterParentheses(activated.expression())));
        }

        /**
         * Requires {@link #isEvaluable()}.
         *
         * @return whether the annotation enforces its policy, i.e. its {@code activated} argument is absent or {@code true}
         */
        boolean isActivated() {
            Argument activated = arguments.get(ACTIVATED_ARGUMENT);
            return activated == null || !FALSE_LITERAL.equals(withoutOuterParentheses(activated.expression()));
        }

        /**
         * Requires {@link #isEvaluable()}. Ares resolves the policy path relative to the root of the tests repository, so the path is returned in its
         * normalized form relative to that root: {@code ./SecurityPolicy.yaml} and {@code test/../SecurityPolicy.yaml} both are {@code SecurityPolicy.yaml}.
         *
         * @return the policy file the annotation points to; empty if it has no {@code value}, in which case Ares applies its strict default policy
         */
        Optional<String> policyPath() {
            Argument value = arguments.get(VALUE_ARGUMENT);
            return value == null ? Optional.empty() : plainStringContent(value).map(AresPolicyAnnotations::normalizePath);
        }

        /**
         * @return the content of the argument if it is a plain string literal, i.e. one without escape sequences and not a text block, optionally in
         *         parentheses; empty for any other expression
         */
        private static Optional<String> plainStringContent(Argument argument) {
            String literal = withoutOuterParentheses(argument.expression());
            boolean plainString = literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"") && !literal.startsWith(TEXT_BLOCK_DELIMITER)
                    && literal.indexOf('\\') < 0 && literal.indexOf('"', 1) == literal.length() - 1;
            return plainString ? Optional.of(literal.substring(1, literal.length() - 1)) : Optional.empty();
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
    }

    /**
     * @return the path in normalized form with forward slashes, e.g. {@code SecurityPolicy.yaml} for {@code ./SecurityPolicy.yaml}; the path as written
     *         if it is not a valid path
     */
    /**
     * @param packageName a package name
     * @return a pattern of the package and the dot after it as Java allows them to be written in the masked source: whitespace may surround each dot
     */
    static String packagePrefix(String packageName) {
        StringJoiner pattern = new StringJoiner("\\s*+\\.\\s*+", "", "\\s*+\\.\\s*+");
        for (String segment : packageName.split("\\.")) {
            pattern.add(Pattern.quote(segment));
        }
        return pattern.toString();
    }

    private static String normalizePath(String path) {
        try {
            String normalized = Path.of(path).normalize().toString().replace('\\', '/');
            return normalized.isEmpty() ? path : normalized;
        }
        catch (InvalidPathException _) {
            return path;
        }
    }

    private AresPolicyAnnotations() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Switches every Ares {@code @Policy} annotation of the source on or off, see {@link #setActive(String, Predicate)}.
     *
     * @param source the content of a Java file
     * @param active whether the annotations should enforce their policy
     * @return the rewritten source
     */
    static String setActive(String source, boolean active) {
        return setActive(source, _ -> active);
    }

    /**
     * Switches every Ares {@code @Policy} annotation of the source on or off. Only the {@code activated} argument changes; the policy path and every other
     * argument are kept, so switching an annotation off and on again restores it exactly. An annotation that already has the requested state is not touched.
     *
     * @param source the content of a Java file
     * @param active whether each annotation should enforce its policy
     * @return the rewritten source
     */
    static String setActive(String source, Predicate<Annotation> active) {
        StringBuilder result = new StringBuilder(source);
        List<Annotation> annotations = find(source);
        // Replace from the end, so the offsets of the annotations before stay valid.
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation annotation = annotations.get(i);
            boolean activate = active.test(annotation);
            if (annotation.isActivated() != activate) {
                result.replace(annotation.start(), annotation.end(), render(annotation, activate));
            }
        }
        return result.toString();
    }

    /**
     * Renders an annotation that points to another policy file, or to none. Only the {@code value} argument changes; an unnamed value stays unnamed if it
     * remains the only argument, and the {@code activated} argument and every other argument are kept. Without a policy file the annotation makes Ares apply
     * its strict default policy.
     *
     * @param annotation an evaluable annotation, see {@link Annotation#isEvaluable()}
     * @param policyPath the validated path of the policy file relative to the repository root, without quotes and backslashes; null for none
     * @return the annotation as it should be written
     */
    static String withPolicyPath(Annotation annotation, @Nullable String policyPath) {
        Map<String, Argument> arguments = new LinkedHashMap<>();
        if (policyPath != null) {
            String literal = quote(policyPath);
            Argument previous = annotation.arguments().get(VALUE_ARGUMENT);
            // A value that was written without its name stays so while it is the only argument; a new one is written with its name, like the templates do.
            boolean named = previous == null || previous.named() || annotation.arguments().keySet().stream().anyMatch(name -> !VALUE_ARGUMENT.equals(name));
            arguments.put(VALUE_ARGUMENT, new Argument(named, named ? VALUE_ARGUMENT + " = " + literal : literal, literal));
        }
        annotation.arguments().forEach((name, argument) -> {
            if (!VALUE_ARGUMENT.equals(name)) {
                arguments.put(name, argument);
            }
        });
        if (arguments.isEmpty()) {
            return annotation.name();
        }
        StringJoiner rendered = new StringJoiner(", ", annotation.name() + "(", ")");
        arguments.values().forEach(argument -> rendered.add(arguments.size() == 1 ? argument.text() : argument.render()));
        return rendered.toString();
    }

    /**
     * @return whether the arguments of the method annotation other than {@code value} and {@code activated} differ from those of the class annotation, also
     *         when the method annotation leaves out one the class annotation sets, e.g. {@code withinPath}, so Ares applies its default for the method
     */
    private static boolean hasOwnArguments(Annotation methodAnnotation, Annotation classAnnotation) {
        return !otherArguments(methodAnnotation).equals(otherArguments(classAnnotation));
    }

    private static Map<String, String> otherArguments(Annotation annotation) {
        Map<String, String> arguments = new LinkedHashMap<>();
        annotation.arguments().forEach((name, argument) -> {
            if (!VALUE_ARGUMENT.equals(name) && !ACTIVATED_ARGUMENT.equals(name)) {
                arguments.put(name, argument.expression());
            }
        });
        return arguments;
    }

    /**
     * Renders the annotation for a test method, written like the annotation of its class: with the same name and the same {@code withinPath} argument, so
     * Ares resolves the policy file the same way.
     *
     * @param classAnnotation the evaluable annotation of the test class
     * @param policyPath      the validated path of the policy file relative to the repository root, without quotes and backslashes
     * @return the annotation as it should be written
     */
    static String methodAnnotation(Annotation classAnnotation, String policyPath) {
        StringJoiner rendered = new StringJoiner(", ", classAnnotation.name() + "(", ")");
        rendered.add(VALUE_ARGUMENT + " = " + quote(policyPath));
        Argument withinPath = classAnnotation.arguments().get(WITHIN_PATH_ARGUMENT);
        if (withinPath != null) {
            rendered.add(withinPath.render());
        }
        return rendered.toString();
    }

    /**
     * Rewrites the {@code @Policy} annotations of a test class so that its test cases use the given policy files: the annotation of the class points to
     * {@code classPolicyPath}, every test method in {@code methodPolicyPaths} gets an annotation that points to its file, the annotations of the methods in
     * {@code keptMethods} stay as they are, and the annotations of the other methods are removed, except one whose other arguments differ from those of the
     * class annotation, e.g. another or no {@code withinPath}, which points to {@code classPolicyPath} if the class has one. Annotations that already point to the right file are
     * not
     * touched, and new method annotations go on their own line above the method's first test annotation, indented like it.
     *
     * @param path              the path of the file relative to the repository root
     * @param source            the content of a Java file with exactly one test class, whose annotation is evaluable
     * @param classPolicyPath   the validated policy path for the class annotation; null for the strict default policy
     * @param methodPolicyPaths the validated policy path for each test method that uses another policy than the class
     * @param keptMethods       the test methods whose annotation overrides the class without a policy file and stays, such as a switched-off one
     * @return the rewritten source
     */
    static String assign(String path, String source, @Nullable String classPolicyPath, Map<String, String> methodPolicyPaths, Set<String> keptMethods) {
        AresTestSources.TypeDeclaration type = AresTestSources.relevantType(AresTestSources.analyze(path, source)).filter(declaration -> declaration.policies().size() == 1)
                .orElseThrow(() -> new IllegalArgumentException("The file " + path + " has no test class with one @Policy annotation"));
        Annotation classAnnotation = type.policies().getFirst();
        // Each edit replaces the range [start, end) of the original source; they are applied from the end so the offsets of the others stay valid.
        List<int[]> ranges = new ArrayList<>();
        List<String> replacements = new ArrayList<>();
        if (!classAnnotation.policyPath().equals(Optional.ofNullable(classPolicyPath))) {
            ranges.add(new int[] { classAnnotation.start(), classAnnotation.end() });
            replacements.add(withPolicyPath(classAnnotation, classPolicyPath));
        }
        type.methodPolicies().forEach((method, annotation) -> {
            // A method annotation with arguments of its own, e.g. another withinPath, is not redundant under a class annotation with a policy file: it points
            // to that policy instead of being removed, so its arguments stay.
            String target = methodPolicyPaths.getOrDefault(method,
                    classPolicyPath != null && !keptMethods.contains(method) && hasOwnArguments(annotation, classAnnotation) ? classPolicyPath : null);
            if (target == null && keptMethods.contains(method)) {
                return;
            }
            if (target == null) {
                ranges.add(lineRange(source, annotation.start(), annotation.end()));
                replacements.add("");
            }
            else if (!annotation.policyPath().equals(Optional.of(target))) {
                ranges.add(new int[] { annotation.start(), annotation.end() });
                replacements.add(withPolicyPath(annotation, target));
            }
        });
        Map<String, Integer> testAnnotations = AresTestCases.testAnnotationOffsets(source);
        String lineSeparator = lineSeparatorOf(source);
        methodPolicyPaths.forEach((method, target) -> {
            if (type.methodPolicies().containsKey(method)) {
                return;
            }
            Integer offset = testAnnotations.get(method);
            if (offset == null) {
                throw new IllegalArgumentException("The test class in " + path + " has no test case " + method);
            }
            int lineStart = lineStart(source, offset);
            String indentation = source.substring(lineStart, offset);
            boolean ownLine = indentation.isBlank();
            ranges.add(new int[] { ownLine ? lineStart : offset, ownLine ? lineStart : offset });
            replacements.add(ownLine ? indentation + methodAnnotation(classAnnotation, target) + lineSeparator : methodAnnotation(classAnnotation, target) + " ");
        });
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < ranges.size(); index++) {
            order.add(index);
        }
        order.sort((first, second) -> Integer.compare(ranges.get(second)[0], ranges.get(first)[0]));
        StringBuilder result = new StringBuilder(source);
        for (int index : order) {
            result.replace(ranges.get(index)[0], ranges.get(index)[1], replacements.get(index));
        }
        return result.toString();
    }

    /**
     * @return the offset where the line of the offset starts; a line ends at a carriage return, a line feed or both (JLS 3.4)
     */
    private static int lineStart(String source, int offset) {
        return Math.max(source.lastIndexOf('\n', offset - 1), source.lastIndexOf('\r', offset - 1)) + 1;
    }

    /**
     * @return the line terminator the source uses, so that a new line matches the lines around it
     */
    private static String lineSeparatorOf(String source) {
        if (source.contains("\r\n")) {
            return "\r\n";
        }
        return source.indexOf('\r') >= 0 && source.indexOf('\n') < 0 ? "\r" : "\n";
    }

    /**
     * @return the range to remove for an annotation: its whole line if nothing else is on it, otherwise the annotation and the whitespace after it
     */
    private static int[] lineRange(String source, int start, int end) {
        int lineStart = lineStart(source, start);
        int after = end;
        while (after < source.length() && (source.charAt(after) == ' ' || source.charAt(after) == '\t')) {
            after++;
        }
        boolean aloneOnLine = source.substring(lineStart, start).isBlank() && (after == source.length() || source.charAt(after) == '\n' || source.charAt(after) == '\r');
        if (!aloneOnLine) {
            return new int[] { start, after };
        }
        int lineEnd = after;
        if (lineEnd < source.length() && source.charAt(lineEnd) == '\r') {
            lineEnd++;
        }
        if (lineEnd < source.length() && source.charAt(lineEnd) == '\n') {
            lineEnd++;
        }
        return new int[] { lineStart, lineEnd };
    }

    private static String quote(String policyPath) {
        if (policyPath.indexOf('"') >= 0 || policyPath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("A policy path must not contain quotes or backslashes: " + policyPath);
        }
        return "\"" + policyPath + "\"";
    }

    private static String render(Annotation annotation, boolean active) {
        Map<String, Argument> arguments = new LinkedHashMap<>(annotation.arguments());
        if (active) {
            arguments.remove(ACTIVATED_ARGUMENT);
        }
        else {
            arguments.put(ACTIVATED_ARGUMENT, new Argument(true, ACTIVATED_ARGUMENT + " = " + FALSE_LITERAL, FALSE_LITERAL));
        }
        if (arguments.isEmpty()) {
            // A comment before the argument list stays, and so do its parentheses, because a line comment would otherwise swallow the code after them.
            return annotation.beforeArguments().isBlank() ? annotation.name() : annotation.name() + annotation.beforeArguments() + "()";
        }
        StringJoiner rendered = new StringJoiner(", ", annotation.name() + annotation.beforeArguments() + "(", ")");
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
        Sources sources = new Sources(source, withoutComments, masked);
        List<Annotation> annotations = new ArrayList<>();
        Matcher names = POLICY_ANNOTATION_NAME.matcher(masked);
        while (names.find()) {
            boolean qualified = names.group(1) != null;
            if (qualified || (simpleNameIsAres && !isPartOfQualifiedName(masked, names.start()) && !isShadowedAt(masked, "Policy", names.start()))) {
                annotation(sources, names.start(), names.end()).ifPresent(annotations::add);
            }
        }
        return annotations;
    }

    /**
     * @param nameStart the offset of the {@code @} of an Ares {@code @Policy} annotation
     * @param nameEnd   the offset after its name
     * @return the annotation with its arguments; empty if it is incomplete, e.g. in a file that does not compile, so that it is left alone
     */
    private static Optional<Annotation> annotation(Sources sources, int nameStart, int nameEnd) {
        String masked = sources.masked();
        int end = nameEnd;
        int open = skipWhitespace(masked, end);
        Map<String, Argument> arguments = new LinkedHashMap<>();
        String beforeArguments = "";
        if (open < masked.length() && masked.charAt(open) == '(') {
            int close = matchingParenthesis(masked, open);
            if (close < 0) {
                return Optional.empty();
            }
            arguments = parseArguments(sources, open + 1, close);
            beforeArguments = sources.original().substring(nameEnd, open);
            end = close + 1;
        }
        return Optional.of(new Annotation(nameStart, end, sources.original().substring(nameStart, nameEnd), beforeArguments, arguments));
    }

    /**
     * @return whether the character before the {@code @} makes it part of a longer name, which then is not the simple Ares annotation
     */
    private static boolean isPartOfQualifiedName(String masked, int at) {
        return at > 0 && (Character.isJavaIdentifierPart(masked.charAt(at - 1)) || masked.charAt(at - 1) == '.');
    }

    /**
     * A type that the file declares itself, e.g. {@code @interface Policy}, shadows a type of the same name that an import provides (JLS 6.4.1): a
     * top-level type in the whole file, a member or local type only within the body that declares it. Where it is shadowed, the simple name does not
     * refer to Ares.
     *
     * @param masked     the source with comments and literals masked
     * @param simpleName the simple name of a type
     * @param offset     where the simple name is used
     * @return whether a type that the file declares shadows the simple name at the offset
     */
    static boolean isShadowedAt(String masked, String simpleName, int offset) {
        Matcher declarations = TYPE_DECLARATION.matcher(masked);
        while (declarations.find()) {
            if (declarations.group(2).equals(simpleName) && isInScopeOf(masked, declarations.start(), offset)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the offset lies in the scope of the declaration that starts at {@code declarationStart}: the whole file for a top-level declaration,
     *         otherwise the body that contains it
     */
    private static boolean isInScopeOf(String masked, int declarationStart, int offset) {
        int scopeStart = enclosingOpeningBrace(masked, declarationStart);
        if (scopeStart < 0) {
            return true;
        }
        int scopeEnd = matchingBrace(masked, scopeStart);
        return offset > scopeStart && (scopeEnd < 0 || offset < scopeEnd);
    }

    /**
     * @return the offset of the innermost brace that is open at the offset; -1 at the top level of the file
     */
    private static int enclosingOpeningBrace(String masked, int offset) {
        int depth = 0;
        for (int index = offset - 1; index >= 0; index--) {
            char character = masked.charAt(index);
            if (character == '}') {
                depth++;
            }
            else if (character == '{') {
                if (depth == 0) {
                    return index;
                }
                depth--;
            }
        }
        return -1;
    }

    /**
     * @return the offset of the brace that closes the one at {@code open}, or -1 if there is none
     */
    static int matchingBrace(String masked, int open) {
        return matchingBracket(masked, open, '{', '}');
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
    static int matchingParenthesis(String masked, int open) {
        return matchingBracket(masked, open, '(', ')');
    }

    /**
     * @return the offset of the {@code closing} bracket that closes the {@code opening} one at {@code open}, counting nested pairs, or -1 if there is none
     */
    private static int matchingBracket(String masked, int open, char opening, char closing) {
        int depth = 0;
        for (int index = open; index < masked.length(); index++) {
            char character = masked.charAt(index);
            if (character == opening) {
                depth++;
            }
            else if (character == closing) {
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
                index = maskUntil(source, masked, index, lineEnd(source, index), 0);
            }
            else if (source.startsWith("/*", index)) {
                int close = source.indexOf("*/", index + 2);
                index = maskUntil(source, masked, index, close < 0 ? -1 : close + 2, 0);
            }
            else if (source.startsWith(TEXT_BLOCK_DELIMITER, index)) {
                masked.append(TEXT_BLOCK_DELIMITER);
                index = maskLiteral(source, masked, index + 3, TEXT_BLOCK_DELIMITER, maskLiterals);
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
     * @return the offset of the line terminator that ends the line at the offset, a carriage return or a line feed (JLS 3.4); -1 if it is the last line
     */
    private static int lineEnd(String source, int from) {
        for (int index = from; index < source.length(); index++) {
            if (isLineTerminator(source.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isLineTerminator(char character) {
        return character == '\n' || character == '\r';
    }

    /**
     * Masks the source from {@code from} up to {@code to} (exclusive, or the end of the source if negative) and appends {@code keep} characters unmasked.
     */
    private static int maskUntil(String source, StringBuilder masked, int from, int to, int keep) {
        int end = to < 0 ? source.length() : to;
        for (int index = from; index < end; index++) {
            char character = source.charAt(index);
            masked.append(isLineTerminator(character) ? character : ' ');
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
        masked.append(!maskContent || isLineTerminator(character) ? character : ' ');
    }
}
