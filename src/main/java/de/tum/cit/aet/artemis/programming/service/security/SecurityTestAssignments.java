package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * Which security policy each test case uses, as the {@code @Policy} annotations of the tests repository decide it (Objective 2). Ares applies the annotation
 * of a test method instead of the one of its class, and a class annotation without a policy file makes Ares apply its strict default policy.
 * <p>
 * The policy editor shows the assignment of a policy as entries: the fully qualified name of a test class if the class annotation points to the policy and no
 * method annotation overrides it, so that test cases added to the class later use the policy too, and otherwise {@code Class#method} for each test case.
 */
final class SecurityTestAssignments {

    /** Separates the test class from the test case in an entry. */
    static final char METHOD_SEPARATOR = '#';

    private SecurityTestAssignments() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The {@code @Policy} annotations of one test class.
     *
     * @param qualifiedName     the fully qualified name of the class
     * @param sourcePath        the path of its source file relative to the repository root
     * @param active            whether its annotation enforces a policy; the test cases of a switched-off class use none, so they are not assigned
     * @param classPolicyPath   the policy file the class annotation points to; null for the strict default policy
     * @param methodPolicyPaths the policy files the annotations of its test methods point to, by the name of the method
     * @param testCases         the names of its test methods, in their order in the source
     */
    record TestClass(String qualifiedName, String sourcePath, boolean active, @Nullable String classPolicyPath, Map<String, String> methodPolicyPaths, List<String> testCases) {

        /**
         * @return the policy file of each entry of the class, see {@link SecurityTestAssignments}
         */
        Map<String, String> entries() {
            Map<String, String> entries = new LinkedHashMap<>();
            if (!active) {
                return entries;
            }
            boolean overridden = methodPolicyPaths.values().stream().anyMatch(path -> !path.equals(classPolicyPath));
            if (classPolicyPath != null && !overridden) {
                entries.put(qualifiedName, classPolicyPath);
                return entries;
            }
            for (String testCase : testCases) {
                String path = methodPolicyPaths.getOrDefault(testCase, classPolicyPath);
                if (path != null) {
                    entries.put(qualifiedName + METHOD_SEPARATOR + testCase, path);
                }
            }
            return entries;
        }

        /**
         * The annotations that give the class the requested entries: an entry of the whole class points the class annotation to its policy; otherwise the class
         * annotation points to no policy and each assigned test case gets an annotation of its own.
         *
         * @param requested the policy file of each requested entry of this class
         * @return the class with the requested annotations
         */
        TestClass with(Map<String, String> requested) {
            String wholeClass = requested.get(qualifiedName);
            if (wholeClass != null) {
                return new TestClass(qualifiedName, sourcePath, active, wholeClass, Map.of(), testCases);
            }
            Map<String, String> methods = new LinkedHashMap<>();
            requested.forEach((entry, path) -> methods.put(entry.substring(entry.indexOf(METHOD_SEPARATOR) + 1), path));
            return new TestClass(qualifiedName, sourcePath, active, null, methods, testCases);
        }
    }

    /**
     * Reads the annotations of the test classes. A class whose annotations the Security Framework cannot evaluate is left out; the repository is then
     * unsupported and not edited anyway.
     *
     * @param javaSources the Java sources of the repository by their path relative to its root
     * @return the test classes, by their fully qualified name, sorted
     */
    static Map<String, TestClass> read(Map<String, String> javaSources) {
        Map<String, TestClass> testClasses = new TreeMap<>();
        SecurityPolicyRepositoryFiles.testClassPaths(javaSources).forEach((qualifiedName, sourcePath) -> {
            String source = javaSources.get(sourcePath);
            AresTestSources.relevantType(AresTestSources.analyze(sourcePath, source))
                    .filter(type -> type.qualifiedName().equals(qualifiedName) && type.policies().size() == 1 && type.policies().getFirst().isEvaluable()).ifPresent(type -> {
                        AresPolicyAnnotations.Annotation annotation = type.policies().getFirst();
                        Map<String, String> methodPolicyPaths = new LinkedHashMap<>();
                        type.methodPolicies().forEach((method, methodAnnotation) -> {
                            if (methodAnnotation.isEvaluable() && methodAnnotation.isActivated()) {
                                methodAnnotation.policyPath().ifPresent(path -> methodPolicyPaths.put(method, path));
                            }
                        });
                        testClasses.put(qualifiedName, new TestClass(qualifiedName, sourcePath, annotation.isActivated(), annotation.policyPath().orElse(null),
                                Map.copyOf(methodPolicyPaths), AresTestCases.testCases(source)));
                    });
        });
        return testClasses;
    }

    /**
     * @param testClasses the test classes
     * @return the entries of each policy file, sorted by the path of the file and the entry
     */
    static Map<String, List<String>> entriesByPolicy(Collection<TestClass> testClasses) {
        Map<String, List<String>> entries = new TreeMap<>();
        testClasses.forEach(testClass -> testClass.entries().forEach((entry, path) -> entries.computeIfAbsent(path, _ -> new ArrayList<>()).add(entry)));
        entries.values().forEach(list -> list.sort(String::compareTo));
        return entries;
    }

    /**
     * @param entry an entry, see {@link SecurityTestAssignments}
     * @return the fully qualified name of its test class
     */
    static String testClassOf(String entry) {
        int separator = entry.indexOf(METHOD_SEPARATOR);
        return separator < 0 ? entry : entry.substring(0, separator);
    }

    /**
     * @param testClass a test class
     * @param entry     an entry of the class
     * @return whether the entry names the class or one of its test cases
     */
    static boolean isValidEntry(TestClass testClass, String entry) {
        int separator = entry.indexOf(METHOD_SEPARATOR);
        return separator < 0 ? Objects.equals(entry, testClass.qualifiedName()) : testClass.testCases().contains(entry.substring(separator + 1));
    }
}
