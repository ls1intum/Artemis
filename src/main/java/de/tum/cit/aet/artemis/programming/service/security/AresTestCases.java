package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
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

    /**
     * The annotations that make a method a test case: JUnit Jupiter, the Ares markers and jqwik. The qualifier repeats possessively, so long qualified names do
     * not grow the stack; giving back a qualifier part could only match a nested type such as {@code @Test.Inner}, which is no test annotation.
     */
    private static final Pattern TEST_ANNOTATION = Pattern
            .compile("@\\s*(?:[\\w$]+\\s*\\.\\s*)*+(?:Test|PublicTest|HiddenTest|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|Property|Example)\\b");

    /** The name of any annotation, also a qualified one. Its parts repeat possessively, so long qualified names do not grow the stack. */
    private static final Pattern ANNOTATION_NAME = Pattern.compile("@\\s*[\\w$]+(?:\\s*\\.\\s*[\\w$]+)*+");

    /** A complete Java identifier, also with letters outside ASCII, that is followed by an opening parenthesis. */
    private static final Pattern IDENTIFIER_BEFORE_PARENTHESIS = Pattern
            .compile("(?<!\\p{javaJavaIdentifierPart})(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\s*\\(");

    private static final Set<String> NOT_METHOD_NAMES = Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "new");

    private AresTestCases() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param javaSources the Java sources of the repository by their path relative to its root
     * @return the test classes with their test cases, and the other top-level classes of the sources as candidates for test code, sorted by name
     */
    static SecurityTestStructureDTO structure(Map<String, String> javaSources) {
        Map<String, SecurityTestAssignments.TestClass> testClasses = SecurityTestAssignments.read(javaSources);
        Set<String> testClassNames = SecurityTestAssignments.testClassPaths(javaSources).keySet();
        Map<String, SecurityTestStructureDTO.TestClassDTO> classes = new TreeMap<>();
        for (Map.Entry<String, String> source : javaSources.entrySet()) {
            for (AresTestSources.TypeDeclaration type : AresTestSources.analyze(source.getKey(), source.getValue()).topLevelTypes()) {
                String name = type.qualifiedName();
                SecurityTestAssignments.TestClass testClass = testClasses.get(name);
                if (testClass != null) {
                    classes.put(name,
                            new SecurityTestStructureDTO.TestClassDTO(name, testClass.testCases(), true, !testClass.active(),
                                    testClass.testCases().stream().filter(testClass.switchedOff()::contains).toList(),
                                    testClass.testCases().stream().filter(testClass.overloaded()::contains).toList()));
                }
                else if (!testClassNames.contains(name)) {
                    classes.putIfAbsent(name, new SecurityTestStructureDTO.TestClassDTO(name, List.of(), false, false, List.of(), List.of()));
                }
            }
        }
        return new SecurityTestStructureDTO(List.copyOf(classes.values()));
    }

    /**
     * @param source the content of a Java file
     * @return the names of its test methods in the order of the source, each once
     */
    static List<String> testCases(String source) {
        return new ArrayList<>(testAnnotationOffsets(source).keySet());
    }

    /**
     * @param source the content of a Java file
     * @return for each test method, in the order of the source, the offset of its first test annotation; a new annotation of the method goes before it
     */
    static Map<String, Integer> testAnnotationOffsets(String source) {
        return testMethods(source).offsets();
    }

    /**
     * @param source the content of a Java file
     * @return the names of the test cases that more than one test method has, e.g. {@code @Test void check()} and {@code @ParameterizedTest void check(int)}:
     *         an entry {@code Class#check} cannot name one of them, so they cannot be assigned one by one
     */
    static Set<String> overloadedTestCases(String source) {
        return testMethods(source).overloaded();
    }

    /**
     * The test methods of a source.
     *
     * @param offsets    for each name, the offset of the first test annotation of the first method with it
     * @param overloaded the names that more than one test method has
     */
    private record TestMethods(Map<String, Integer> offsets, Set<String> overloaded) {
    }

    private static TestMethods testMethods(String source) {
        String masked = AresPolicyAnnotations.mask(source);
        Map<String, Integer> offsets = new LinkedHashMap<>();
        Map<String, Integer> declarations = new HashMap<>();
        Set<String> overloaded = new LinkedHashSet<>();
        Matcher annotation = TEST_ANNOTATION.matcher(masked);
        while (annotation.find()) {
            MatchResult method = methodAfter(masked, annotation.end());
            if (method != null) {
                String name = method.group(1);
                offsets.putIfAbsent(name, annotation.start());
                // Two test annotations of one method find the same declaration; another declaration with the name is an overload.
                Integer declaration = declarations.putIfAbsent(name, method.start(1));
                if (declaration != null && declaration != method.start(1)) {
                    overloaded.add(name);
                }
            }
        }
        return new TestMethods(offsets, overloaded);
    }

    /**
     * @return the name of the method that the annotation ending at the offset annotates; null if none follows before the next body or statement
     */
    static @Nullable String methodNameAfter(String masked, int offset) {
        MatchResult method = methodAfter(masked, offset);
        return method == null ? null : method.group(1);
    }

    /**
     * @return the match of the name of the method that the annotation ending at the offset annotates, its group 1 being the name; null if none follows
     */
    private static @Nullable MatchResult methodAfter(String masked, int offset) {
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
            // Transparent bounds let the look-behind see the character before the region, so no match starts inside an identifier.
            Matcher method = IDENTIFIER_BEFORE_PARENTHESIS.matcher(masked).region(position, masked.length()).useTransparentBounds(true);
            if (method.lookingAt()) {
                return NOT_METHOD_NAMES.contains(method.group(1)) ? null : method.toMatchResult();
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
