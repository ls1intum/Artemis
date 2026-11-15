package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;

/**
 * Lists the test cases of the test classes of a tests repository, so instructors can assign single test cases to a security policy.
 * <p>
 * Like {@link AresTestSources}, it reads the sources without compiling them: comments and literals are masked first, then every method annotated with a
 * JUnit or Ares test annotation is a test case. The annotations between the test annotation and the method name, such as {@code @Timeout(5)} or
 * {@code @DisplayName("...")}, are skipped together with their arguments.
 */
final class AresTestCases {

    /** The annotations that make a method a test case: JUnit Jupiter, the Ares markers and jqwik. */
    private static final Pattern TEST_ANNOTATION = Pattern
            .compile("@\\s*(?:[\\w$]+\\s*\\.\\s*)*(?:Test|PublicTest|HiddenTest|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|Property|Example)\\b");

    /** The name of any annotation, also a qualified one. */
    private static final Pattern ANNOTATION_NAME = Pattern.compile("@\\s*[\\w$]+(?:\\s*\\.\\s*[\\w$]+)*");

    private static final Pattern IDENTIFIER_BEFORE_PARENTHESIS = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(");

    private static final Set<String> NOT_METHOD_NAMES = Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "new");

    private AresTestCases() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param javaSources the Java sources of the repository by their path relative to its root
     * @param testClasses the fully qualified names of the test classes
     * @return the test classes with their test cases, sorted by name
     */
    static SecurityTestStructureDTO structure(Map<String, String> javaSources, Set<String> testClasses) {
        Map<String, List<String>> testCasesByClass = new TreeMap<>();
        for (Map.Entry<String, String> source : javaSources.entrySet()) {
            AresTestSources.Summary summary = AresTestSources.analyze(source.getKey(), source.getValue());
            AresTestSources.relevantType(summary).filter(type -> testClasses.contains(type.qualifiedName()))
                    .ifPresent(type -> testCasesByClass.put(type.qualifiedName(), testCases(source.getValue())));
        }
        testClasses.forEach(testClass -> testCasesByClass.putIfAbsent(testClass, List.of()));
        return new SecurityTestStructureDTO(
                testCasesByClass.entrySet().stream().map(entry -> new SecurityTestStructureDTO.TestClassDTO(entry.getKey(), entry.getValue())).toList());
    }

    /**
     * @param source the content of a Java file
     * @return the names of its test methods in the order of the source, each once
     */
    static List<String> testCases(String source) {
        String masked = AresPolicyAnnotations.mask(source);
        Set<String> names = new LinkedHashSet<>();
        Matcher annotation = TEST_ANNOTATION.matcher(masked);
        while (annotation.find()) {
            String name = methodNameAfter(masked, annotation.end());
            if (name != null) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * @return the name of the method that the annotation ending at the offset annotates; null if none follows before the next body or statement
     */
    private static @Nullable String methodNameAfter(String masked, int offset) {
        int position = skipArguments(masked, skipWhitespace(masked, offset));
        while (position < masked.length()) {
            char character = masked.charAt(position);
            if (character == '{' || character == ';' || character == '}' || character == '=') {
                return null;
            }
            if (character == '@') {
                Matcher name = ANNOTATION_NAME.matcher(masked).region(position, masked.length());
                if (!name.lookingAt()) {
                    return null;
                }
                position = skipArguments(masked, skipWhitespace(masked, name.end()));
                continue;
            }
            Matcher method = IDENTIFIER_BEFORE_PARENTHESIS.matcher(masked).region(position, masked.length());
            if (method.lookingAt()) {
                String name = method.group(1);
                return NOT_METHOD_NAMES.contains(name) ? null : name;
            }
            position++;
        }
        return null;
    }

    /**
     * @return the offset after the argument list that starts at the offset, or the offset itself if none starts there
     */
    private static int skipArguments(String masked, int offset) {
        if (offset >= masked.length() || masked.charAt(offset) != '(') {
            return offset;
        }
        int depth = 0;
        for (int position = offset; position < masked.length(); position++) {
            char character = masked.charAt(position);
            if (character == '(') {
                depth++;
            }
            else if (character == ')' && --depth == 0) {
                return position + 1;
            }
        }
        return masked.length();
    }

    private static int skipWhitespace(String text, int offset) {
        int position = offset;
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }
}
