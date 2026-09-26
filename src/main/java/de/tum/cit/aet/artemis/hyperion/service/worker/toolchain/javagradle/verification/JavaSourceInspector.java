package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical Java inspection for the generated-test safety gates. Candidates may be incomplete, so malformed annotations fail closed rather than requiring a compiler parser.
 * Comments and literals never supply annotation or class-scope evidence; masking preserves offsets into the original source for trusted literal arguments.
 */
final class JavaSourceInspector {

    /**
     * The trusted {@code @StrictTimeout} range, in seconds. The gate exists to stop an unbounded test from hanging grading, not to pin one magic constant: Artemis's own seeded
     * structural test classes ({@code templates/java/test/testFiles/structural/}) carry {@code @StrictTimeout(10)} for {@link StructuralOracleSeeder}'s reflection-heavy
     * tests, so demanding exactly {@code 1} would falsely reject Artemis's own trusted output. Only an unset, shadowed, or unbounded timeout is rejected.
     */
    static final int MIN_STRICT_TIMEOUT_SECONDS = 1;

    static final int MAX_STRICT_TIMEOUT_SECONDS = 15;

    private static final Pattern JAVA_PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

    private static final Pattern JAVA_CLASS_DECLARATION = Pattern.compile("\\b(?:class|interface|enum|record)\\s+[\\w$]+");

    private static final Pattern JAVA_IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*;");

    private static final Pattern LOCAL_TYPE = Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)");

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u+");

    private static final Pattern ANNOTATION_NAME = Pattern
            .compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(?:\\s*\\.\\s*\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");

    private static final Pattern SIMPLE_ANNOTATION_ARGUMENT = Pattern.compile("[0-9]+|\"(?:build|build/classes/java/test)\"");

    private JavaSourceInspector() {
    }

    private record JavaClassAnnotation(int depth, String annotations) {
    }

    private record JavaAnnotation(String text, int end) {
    }

    /**
     * What the Ares convention gate needs to know about one Java test source.
     *
     * @param hasTestMethods                  whether the file declares any JUnit test method at all (a file without one is not held to the class-level conventions)
     * @param classWithMissingAresAnnotations whether some test method's enclosing class lacks the trusted {@code @Public}/{@code @WhitelistPath}/{@code @BlacklistPath} set
     * @param testMethodWithoutStrictTimeout  whether some test method has no bounded {@code @StrictTimeout}, on the method or on its class
     * @param unsupportedAnnotationSyntax     whether malformed or composed annotations prevent safe identification of executable tests
     */
    record JavaTestAnnotationSummary(boolean hasTestMethods, boolean classWithMissingAresAnnotations, boolean testMethodWithoutStrictTimeout, boolean unsupportedAnnotationSyntax) {
    }

    /**
     * Applies JLS 3.3 before comment/literal masking. Eligibility uses the translated backslash parity, but output is never recursively decoded.
     * Returns null for a malformed eligible escape, which the annotation gate must reject even in an unchanged declaration.
     */
    private static String translateUnicodeEscapes(String source) {
        StringBuilder translated = new StringBuilder(source.length());
        int backslashes = 0;
        boolean previousWasEscape = false;
        for (int offset = 0; offset < source.length(); offset++) {
            char current = source.charAt(offset);
            boolean escaped = false;
            if (current == '\\' && (previousWasEscape || backslashes % 2 == 0) && offset + 1 < source.length() && source.charAt(offset + 1) == 'u') {
                int digits = offset + 2;
                while (digits < source.length() && source.charAt(digits) == 'u') {
                    digits++;
                }
                if (source.length() - digits < 4) {
                    return null;
                }
                int value = 0;
                for (int index = digits; index < digits + 4; index++) {
                    char digit = source.charAt(index);
                    // JLS HexDigit is ASCII only; Character.digit also accepts non-ASCII numerals.
                    int hex = digit <= 'f' ? Character.digit(digit, 16) : -1;
                    if (hex < 0) {
                        return null;
                    }
                    value = value * 16 + hex;
                }
                current = (char) value;
                offset = digits + 3;
                escaped = true;
            }
            translated.append(current);
            backslashes = current == '\\' ? backslashes + 1 : 0;
            previousWasEscape = escaped;
        }
        return translated.toString();
    }

    /**
     * Resolves simple annotation names against the file's imports, so a package-local look-alike (a self-declared {@code Public}) cannot pass for the trusted annotation.
     */
    static JavaTestAnnotationSummary javaTestAnnotationSummary(String content) {
        content = translateUnicodeEscapes(content);
        if (content == null) {
            return new JavaTestAnnotationSummary(true, true, true, true);
        }
        String withoutComments = stripJavaComments(content);
        String structural = stripJavaTrivia(content, true);
        Set<String> imports = new HashSet<>();
        Matcher importMatcher = JAVA_IMPORT.matcher(structural);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        Matcher localTypeMatcher = LOCAL_TYPE.matcher(structural);
        while (localTypeMatcher.find()) {
            String localType = localTypeMatcher.group(1);
            imports.removeIf(importedType -> importedType.endsWith("." + localType));
        }
        var classes = new ArrayDeque<JavaClassAnnotation>();
        boolean hasTestMethods = false;
        boolean missingClassAnnotations = false;
        boolean missingTimeouts = false;
        int depth = 0;
        StringBuilder annotations = new StringBuilder();
        StringBuilder declaration = new StringBuilder();
        for (int offset = 0; offset < structural.length(); offset++) {
            char current = structural.charAt(offset);
            if (current == '@') {
                JavaAnnotation annotation = readAnnotation(withoutComments, structural, offset);
                if (annotation == null) {
                    return new JavaTestAnnotationSummary(true, true, true, true);
                }
                if (annotation.text().equals("@interface")) {
                    // A meta-annotation can make methods in other files executable. Require direct JUnit annotations instead of guessing a transitive type graph.
                    if (hasJUnitTestAnnotation(annotations.toString())) {
                        return new JavaTestAnnotationSummary(true, true, true, true);
                    }
                    declaration.append(" interface ");
                }
                else {
                    annotations.append(annotation.text()).append('\n');
                }
                offset = annotation.end() - 1;
            }
            else if (current == '{' || current == ';') {
                String annotationBlock = annotations.toString();
                if (current == '{' && JAVA_CLASS_DECLARATION.matcher(declaration).find()) {
                    classes.push(new JavaClassAnnotation(depth + 1, annotationBlock));
                }
                else if (hasJUnitTestAnnotation(annotationBlock)) {
                    hasTestMethods = true;
                    String classAnnotations = classes.isEmpty() ? "" : classes.peek().annotations();
                    missingClassAnnotations |= !hasAresClassAnnotations(classAnnotations, imports);
                    missingTimeouts |= !hasStrictTimeout(annotationBlock, imports) && !hasStrictTimeout(classAnnotations, imports);
                }
                if (current == '{') {
                    depth++;
                }
                annotations.setLength(0);
                declaration.setLength(0);
            }
            else if (current == '}') {
                if (!classes.isEmpty() && classes.peek().depth() == depth) {
                    classes.pop();
                }
                depth--;
                annotations.setLength(0);
                declaration.setLength(0);
            }
            else {
                declaration.append(current);
            }
        }
        return new JavaTestAnnotationSummary(hasTestMethods, missingClassAnnotations, missingTimeouts, false);
    }

    /** Reads actual annotation tokens from masked source, never annotation-shaped text from their literal arguments. */
    private static JavaAnnotation readAnnotation(String source, String structural, int start) {
        int offset = start + 1;
        while (offset < structural.length() && Character.isWhitespace(structural.charAt(offset))) {
            offset++;
        }
        Matcher name = ANNOTATION_NAME.matcher(structural).region(offset, structural.length());
        if (!name.lookingAt()) {
            return null;
        }
        String annotation = "@" + name.group().replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        offset = name.end();
        while (offset < structural.length() && Character.isWhitespace(structural.charAt(offset))) {
            offset++;
        }
        if (offset >= structural.length() || structural.charAt(offset) != '(') {
            return new JavaAnnotation(annotation, offset);
        }
        int argumentStart = ++offset;
        int parentheses = 1;
        while (offset < structural.length() && parentheses > 0) {
            char current = structural.charAt(offset++);
            if (current == '(') {
                parentheses++;
            }
            else if (current == ')') {
                parentheses--;
            }
        }
        if (parentheses != 0) {
            return null;
        }
        String argument = source.substring(argumentStart, offset - 1).trim();
        // Only the literal path and integer arguments used by the safety contract can supply evidence.
        String trustedArgument = SIMPLE_ANNOTATION_ARGUMENT.matcher(argument).matches() ? argument : "?";
        return new JavaAnnotation(annotation + "(" + trustedArgument + ")", offset);
    }

    private static boolean hasAresClassAnnotations(String annotations, Set<String> imports) {
        return hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.jupiter.Public", "Public", null)
                && hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.WhitelistPath", "WhitelistPath", "\"build\"")
                && hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.BlacklistPath", "BlacklistPath", "\"build/classes/java/test\"");
    }

    private static boolean hasStrictTimeout(String annotations, Set<String> imports) {
        return hasBoundedStrictTimeout(annotations, "de.tum.in.test.api.StrictTimeout")
                || (imports.contains("de.tum.in.test.api.StrictTimeout") && hasBoundedStrictTimeout(annotations, "StrictTimeout"));
    }

    private static boolean hasBoundedStrictTimeout(String annotations, String name) {
        Matcher matcher = Pattern.compile("@" + Pattern.quote(name) + "\\s*\\(\\s*(\\d+)\\s*\\)").matcher(annotations);
        while (matcher.find()) {
            try {
                long seconds = Long.parseLong(matcher.group(1));
                if (seconds >= MIN_STRICT_TIMEOUT_SECONDS && seconds <= MAX_STRICT_TIMEOUT_SECONDS) {
                    return true;
                }
            }
            catch (NumberFormatException e) {
                // An unrepresentably large literal is out of the trusted range; keep scanning further matches on the same element.
            }
        }
        return false;
    }

    /** An annotation counts as trusted only when it is written fully qualified or its simple name resolves through an import that is not shadowed by a local declaration. */
    private static boolean hasTrustedAnnotation(String annotations, Set<String> imports, String qualifiedName, String simpleName, String argument) {
        String suffix = argument == null ? "\\b" : "\\s*\\(\\s*" + Pattern.quote(argument) + "\\s*\\)";
        boolean fullyQualified = Pattern.compile("@" + Pattern.quote(qualifiedName) + suffix).matcher(annotations).find();
        boolean imported = imports.contains(qualifiedName) && Pattern.compile("@" + Pattern.quote(simpleName) + suffix).matcher(annotations).find();
        return fullyQualified || imported;
    }

    private static boolean hasJUnitTestAnnotation(String annotations) {
        return hasAnnotation(annotations, "Test") || hasAnnotation(annotations, "ParameterizedTest") || hasAnnotation(annotations, "RepeatedTest")
                || hasAnnotation(annotations, "TestFactory") || hasAnnotation(annotations, "TestTemplate");
    }

    private static boolean hasAnnotation(String annotations, String simpleName) {
        return Pattern.compile("@(?:[\\w.]+\\.)?" + Pattern.quote(simpleName) + "\\b").matcher(annotations).find();
    }

    /**
     * Blanks out comments while leaving string, character, and text block literals intact, so a {@code //} inside a URL literal is not mistaken for a comment and a commented-out
     * annotation cannot spoof a gate. Every removed character becomes a space and every line terminator is kept, so offsets and line numbers still line up with the original
     * source.
     */
    static String stripJavaComments(String content) {
        return stripJavaTrivia(content, false);
    }

    private static String stripJavaTrivia(String content, boolean maskLiterals) {
        StringBuilder stripped = new StringBuilder(content.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean inTextBlock = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    stripped.append(current);
                }
                else {
                    stripped.append(' ');
                }
            }
            else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    stripped.append("  ");
                    i++;
                }
                else {
                    stripped.append(current == '\n' || current == '\r' ? current : ' ');
                }
            }
            else if (inTextBlock) {
                if (current == '\\' && next != '\0') {
                    stripped.append(maskLiterals ? ' ' : current).append(maskLiterals && next != '\n' && next != '\r' ? ' ' : next);
                    i++;
                }
                else if (content.startsWith("\"\"\"", i)) {
                    stripped.append(maskLiterals ? "   " : "\"\"\"");
                    i += 2;
                    inTextBlock = false;
                }
                else {
                    stripped.append(maskLiterals && current != '\n' && current != '\r' ? ' ' : current);
                }
            }
            else if (inString || inChar) {
                stripped.append(maskLiterals && current != '\n' && current != '\r' ? ' ' : current);
                if (current == '\\' && next != '\0') {
                    stripped.append(maskLiterals && next != '\n' && next != '\r' ? ' ' : next);
                    i++;
                }
                else if ((inString && current == '"') || (inChar && current == '\'')) {
                    inString = false;
                    inChar = false;
                }
            }
            else if (current == '/' && next == '/') {
                inLineComment = true;
                stripped.append("  ");
                i++;
            }
            else if (current == '/' && next == '*') {
                inBlockComment = true;
                stripped.append("  ");
                i++;
            }
            else if (content.startsWith("\"\"\"", i)) {
                stripped.append(maskLiterals ? "   " : "\"\"\"");
                i += 2;
                inTextBlock = true;
            }
            else {
                inString = current == '"';
                inChar = current == '\'';
                stripped.append(maskLiterals && (inString || inChar) ? ' ' : current);
            }
        }
        return stripped.toString();
    }

    static String normalizeBody(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").strip();
    }

    /** Compiler output under {@code target/} or {@code build/} is not a shipping source. */
    static boolean isJavaSource(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.endsWith(".java") && !normalized.startsWith("target/") && !normalized.contains("/target/") && !normalized.startsWith("build/")
                && !normalized.contains("/build/");
    }

    /**
     * Matches a top-level, nested, or secondary declaration, and covers the non-Java declaration keywords too so the same check serves the language-agnostic ownership gates.
     */
    static boolean sourceDeclaresType(String content, String type) {
        if (content == null || type == null) {
            return false;
        }
        String declarationStart = "(?:^|[;{}])\\s*";
        String modifiers = "(?:(?:public|protected|private|static|abstract|final|sealed|non-sealed)\\s+)*";
        return Pattern.compile(declarationStart + modifiers + "(?:class|interface|enum|record|trait|struct|protocol)\\s+" + Pattern.quote(type) + "\\b", Pattern.MULTILINE)
                .matcher(stripJavaComments(content)).find();
    }

    /**
     * Whether a Java source's declared package matches the directory it sits in, relative to one of {@code sourceRoots}. A Unicode escape anywhere before the end of the package
     * declaration is rejected outright: escapes are translated before parsing, so a source spelling part of its package with them declares one package to the compiler while
     * showing a different one to a reader.
     */
    static boolean declaresPackageMatchingPath(String path, String content, List<String> sourceRoots) {
        String sourceRoot = sourceRoots.stream().filter(path::startsWith).findFirst().orElse(null);
        int filenameSeparator = path.lastIndexOf('/');
        if (sourceRoot == null || filenameSeparator < sourceRoot.length() || content == null) {
            return false;
        }
        Matcher matcher = JAVA_PACKAGE_DECLARATION.matcher(stripJavaComments(content));
        if (!matcher.find() || UNICODE_ESCAPE.matcher(content.substring(0, matcher.end())).find()) {
            return false;
        }
        String expectedPackage = path.substring(sourceRoot.length(), filenameSeparator).replace('/', '.');
        return matcher.group(1).equals(expectedPackage);
    }

}
