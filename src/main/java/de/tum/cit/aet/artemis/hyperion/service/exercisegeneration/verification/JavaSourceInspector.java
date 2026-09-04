package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-level inspection of Java sources and Maven manifests: questions about <em>the Java language</em>, as opposed to {@link ExerciseIntegrityGate}'s questions about what makes
 * a generated exercise trustworthy.
 * <p>
 * There is no parser: a candidate under inspection is often broken source that never has to compile for the gates to run, so every function here is total and yields a
 * conservative answer rather than an exception on malformed input.
 * <p>
 * Two invariants the callers depend on, which a "simplification" into regexes would silently break:
 * <ul>
 * <li>{@link #stripJavaComments} preserves the line structure exactly (a removed character becomes a space, a newline stays a newline), because
 * {@link #javaTestAnnotationSummary} matches methods to their enclosing class by line index.</li>
 * <li>{@link #xmlBlocks} is non-greedy per element, so a {@code groupId} from one dependency can never pair with an {@code artifactId} from the next to fake a dependency nobody
 * declared.</li>
 * </ul>
 */
final class JavaSourceInspector {

    /**
     * The trusted {@code @StrictTimeout} range, in seconds. The gate exists to stop an unbounded test from hanging grading, not to pin one magic constant: Artemis's own seeded
     * structural test classes ({@code templates/java/test/testFiles/structural/}) carry {@code @StrictTimeout(10)} for {@link StructuralOracleSeedingService}'s reflection-heavy
     * tests, so demanding exactly {@code 1} would falsely reject Artemis's own trusted output. Only an unset, shadowed, or unbounded timeout is rejected.
     */
    static final int MIN_STRICT_TIMEOUT_SECONDS = 1;

    static final int MAX_STRICT_TIMEOUT_SECONDS = 15;

    private static final Pattern JAVA_PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

    private static final Pattern JAVA_CLASS_DECLARATION = Pattern.compile("\\b(?:public\\s+)?(?:abstract\\s+)?class\\s+\\w+");

    private static final Pattern JAVA_METHOD_DECLARATION = Pattern
            .compile("\\b(?:public|protected|private)?\\s*(?:static\\s+)?[\\w<>\\[\\], ?]+\\s+\\w+\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{}]+)?\\{");

    private JavaSourceInspector() {
    }

    /** One class declaration's annotation block, keyed by the line the declaration starts on so a method can be attributed to the class that encloses it. */
    private record JavaClassAnnotation(int start, String annotations) {
    }

    /**
     * What the Ares convention gate needs to know about one Java test source.
     *
     * @param hasTestMethods                  whether the file declares any JUnit test method at all (a file without one is not held to the class-level conventions)
     * @param classWithMissingAresAnnotations whether some test method's enclosing class lacks the trusted {@code @Public}/{@code @WhitelistPath}/{@code @BlacklistPath} set
     * @param testMethodWithoutStrictTimeout  whether some test method has no bounded {@code @StrictTimeout}, on the method or on its class
     */
    record JavaTestAnnotationSummary(boolean hasTestMethods, boolean classWithMissingAresAnnotations, boolean testMethodWithoutStrictTimeout) {
    }

    /**
     * Resolves simple annotation names against the file's imports, so a package-local look-alike (a self-declared {@code Public}) cannot pass for the trusted annotation.
     */
    static JavaTestAnnotationSummary javaTestAnnotationSummary(String content) {
        String withoutComments = stripJavaComments(content);
        Set<String> imports = new HashSet<>();
        Matcher importMatcher = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*;").matcher(withoutComments);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        Matcher localTypeMatcher = Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)").matcher(withoutComments);
        while (localTypeMatcher.find()) {
            String localType = localTypeMatcher.group(1);
            imports.removeIf(importedType -> importedType.endsWith("." + localType));
        }
        String[] lines = withoutComments.split("\\R", -1);
        List<JavaClassAnnotation> classes = new ArrayList<>();
        boolean hasTestMethods = false;
        boolean missingClassAnnotations = false;
        boolean missingTimeouts = false;
        StringBuilder annotations = new StringBuilder();
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("@")) {
                annotations.append(line).append('\n');
                lineIndex = appendAnnotationContinuation(lines, lineIndex, annotations);
                continue;
            }
            if (annotations.isEmpty()) {
                continue;
            }
            int declarationLine = lineIndex;
            String declaration = line;
            while (!declaration.contains("{") && !declaration.contains(";") && lineIndex + 1 < lines.length) {
                String nextLine = lines[lineIndex + 1].trim();
                if (nextLine.startsWith("@")) {
                    break;
                }
                declaration += " " + nextLine;
                lineIndex++;
            }
            String annotationBlock = annotations.toString();
            if (JAVA_CLASS_DECLARATION.matcher(declaration).find()) {
                classes.add(new JavaClassAnnotation(declarationLine, annotationBlock));
            }
            else if (JAVA_METHOD_DECLARATION.matcher(declaration).find() && hasJUnitTestAnnotation(annotationBlock)) {
                hasTestMethods = true;
                String classAnnotations = enclosingClassAnnotations(classes, declarationLine);
                if (!hasAresClassAnnotations(classAnnotations, imports)) {
                    missingClassAnnotations = true;
                }
                if (!hasStrictTimeout(annotationBlock, imports) && !hasStrictTimeout(classAnnotations, imports)) {
                    missingTimeouts = true;
                }
            }
            annotations.setLength(0);
        }
        return new JavaTestAnnotationSummary(hasTestMethods, missingClassAnnotations, missingTimeouts);
    }

    /** Reads on while the argument list is still open, so a multi-line {@code @StrictTimeout(\n 1)} is one annotation instead of two halves. */
    private static int appendAnnotationContinuation(String[] lines, int startLine, StringBuilder annotations) {
        int parenthesisBalance = parenthesisBalance(lines[startLine]);
        int lineIndex = startLine;
        while (parenthesisBalance > 0 && lineIndex + 1 < lines.length) {
            lineIndex++;
            String line = lines[lineIndex].trim();
            annotations.append(line).append('\n');
            parenthesisBalance += parenthesisBalance(line);
        }
        return lineIndex;
    }

    private static int parenthesisBalance(String line) {
        int balance = 0;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '(') {
                balance++;
            }
            else if (character == ')') {
                balance--;
            }
        }
        return balance;
    }

    private static String enclosingClassAnnotations(List<JavaClassAnnotation> classes, int line) {
        String annotations = "";
        for (JavaClassAnnotation javaClass : classes) {
            if (javaClass.start() > line) {
                break;
            }
            annotations = javaClass.annotations();
        }
        return annotations;
    }

    private static boolean hasAresClassAnnotations(String annotations, Set<String> imports) {
        return hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.jupiter.Public", "Public", null)
                && hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.WhitelistPath", "WhitelistPath", "\"target\"")
                && hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.BlacklistPath", "BlacklistPath", "\"target/test-classes\"");
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
     * Blanks out comments while leaving string and character literals intact, so a {@code //} inside a URL literal is not mistaken for a comment and a commented-out annotation
     * cannot spoof a gate. Every removed character becomes a space and every newline is kept, so offsets and line numbers still line up with the original source.
     */
    static String stripJavaComments(String content) {
        StringBuilder stripped = new StringBuilder(content.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (current == '\n') {
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
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
            }
            else if (inString || inChar) {
                stripped.append(current);
                if (current == '\\' && next != '\0') {
                    stripped.append(next);
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
            else {
                inString = current == '"';
                inChar = current == '\'';
                stripped.append(current);
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
        if (!matcher.find() || Pattern.compile("\\\\u+").matcher(content.substring(0, matcher.end())).find()) {
            return false;
        }
        String expectedPackage = path.substring(sourceRoot.length(), filenameSeparator).replace('/', '.');
        return matcher.group(1).equals(expectedPackage);
    }

    /** Both coordinates must sit in the SAME {@code <dependency>} block; {@code pom} must already have its comments stripped. */
    static boolean hasMavenDependency(String pom, String groupId, String artifactId) {
        return xmlBlocks(pom, "dependency").stream().anyMatch(block -> hasXmlElementText(block, "groupId", groupId) && hasXmlElementText(block, "artifactId", artifactId));
    }

    /** Same same-block requirement as {@link #hasMavenDependency}. */
    static boolean hasMavenPlugin(String pom, String groupId, String artifactId) {
        return xmlBlocks(pom, "plugin").stream().anyMatch(block -> hasXmlElementText(block, "groupId", groupId) && hasXmlElementText(block, "artifactId", artifactId));
    }

    /** Every {@code <element>...</element>} span, matched non-greedily so sibling elements stay separate blocks. */
    private static List<String> xmlBlocks(String content, String element) {
        Matcher matcher = Pattern.compile("(?s)<" + Pattern.quote(element) + "\\b[^>]*>.*?</" + Pattern.quote(element) + ">").matcher(content);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group());
        }
        return blocks;
    }

    /** Only element text is considered, never attribute values. */
    static boolean hasXmlElementText(String content, String element, String expectedText) {
        Matcher matcher = Pattern.compile("(?s)<" + Pattern.quote(element) + "\\b[^>]*>\\s*([^<]*?)\\s*</" + Pattern.quote(element) + ">").matcher(content);
        while (matcher.find()) {
            if (matcher.group(1).contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    /** Removes XML comments so a commented-out declaration cannot satisfy a manifest probe. */
    static String stripXmlComments(String content) {
        return content.replaceAll("(?s)<!--.*?-->", "");
    }
}
