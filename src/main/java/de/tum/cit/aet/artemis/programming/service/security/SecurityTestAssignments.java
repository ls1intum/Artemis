package de.tum.cit.aet.artemis.programming.service.security;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Which security policy each test case uses, as the {@code @Policy} annotations of the tests repository decide it (Objective 2). Ares applies the annotation
 * of a test method instead of the one of its class, and a class annotation without a policy file makes Ares apply its strict default policy.
 * <p>
 * The policy editor shows the assignment of a policy as entries: the fully qualified name of a test class if the class annotation points to the policy and no
 * method annotation overrides it, so that test cases added to the class later use the policy too, and otherwise {@code Class#method} for each test case.
 * <p>
 * A method annotation without a policy file, which gives the test case the strict default policy, and a switched-off method annotation are explicit
 * overrides too: the test case uses no policy file, and rewriting the assignments of its class keeps the annotation unless the test case is assigned.
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
     * @param defaultMethods    the test methods whose annotation gives them the strict default policy, without a policy file
     * @param switchedOff       the test methods whose annotation switches the sandbox off or cannot be evaluated; they cannot be assigned
     * @param overloaded        the test cases that more than one test method has, see {@link AresTestCases#overloadedTestCases(String)}; they cannot be
     *                              assigned one by one
     * @param testCases         the names of its test methods, in their order in the source
     * @param inheritsTests     whether it extends another test class of the repository, whose test methods it inherits; they are not among its test cases
     *                              and use the policy of its class annotation
     */
    record TestClass(String qualifiedName, String sourcePath, boolean active, @Nullable String classPolicyPath, Map<String, String> methodPolicyPaths, Set<String> defaultMethods,
            Set<String> switchedOff, Set<String> overloaded, List<String> testCases, boolean inheritsTests) {

        /**
         * @return the test methods whose annotation stays as it is when the assignments of the class are rewritten, unless the method is assigned
         */
        Set<String> keptMethods() {
            Set<String> kept = new LinkedHashSet<>(defaultMethods);
            kept.addAll(switchedOff);
            return kept;
        }

        /**
         * @return the policy file of each entry of the class, see {@link SecurityTestAssignments}
         */
        Map<String, String> entries() {
            Map<String, String> entries = new LinkedHashMap<>();
            if (!active) {
                return entries;
            }
            boolean overridden = !keptMethods().isEmpty() || methodPolicyPaths.values().stream().anyMatch(path -> !path.equals(classPolicyPath));
            if (classPolicyPath != null && !overridden) {
                entries.put(qualifiedName, classPolicyPath);
                return entries;
            }
            for (String testCase : testCases) {
                if (keptMethods().contains(testCase) || overloaded.contains(testCase)) {
                    continue;
                }
                String path = methodPolicyPaths.getOrDefault(testCase, classPolicyPath);
                if (path != null) {
                    entries.put(qualifiedName + METHOD_SEPARATOR + testCase, path);
                }
            }
            return entries;
        }

        /**
         * The annotations that give the class the requested entries: an entry of the whole class points the class annotation to its policy and removes the
         * overrides without a policy file of its test cases; otherwise the class annotation points to no policy and each assigned test case gets an annotation
         * of its own. The explicit overrides of test cases that are not assigned stay, so that no test case gets more permissions than before, and so do
         * switched-off ones.
         *
         * @param requested the policy file of each requested entry of this class
         * @return the class with the requested annotations
         */
        TestClass with(Map<String, String> requested) {
            Map<String, String> methods = new LinkedHashMap<>();
            requested.forEach((entry, path) -> {
                if (entry.indexOf(METHOD_SEPARATOR) >= 0) {
                    methods.put(entry.substring(entry.indexOf(METHOD_SEPARATOR) + 1), path);
                }
            });
            String requestedClassPolicyPath = requested.get(qualifiedName);
            // An assigned test case loses its override without a policy file; assigning the whole class assigns every test case, while a switched-off one stays.
            Set<String> defaults = requestedClassPolicyPath != null ? new LinkedHashSet<>() : new LinkedHashSet<>(defaultMethods);
            defaults.removeAll(methods.keySet());
            return new TestClass(qualifiedName, sourcePath, active, requestedClassPolicyPath, methods, defaults, switchedOff, overloaded, testCases, inheritsTests);
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
        Map<String, String> testClassPaths = testClassPaths(javaSources);
        testClassPaths.forEach((qualifiedName, sourcePath) -> {
            String source = javaSources.get(sourcePath);
            AresTestSources.relevantType(AresTestSources.analyze(sourcePath, source))
                    .filter(type -> type.qualifiedName().equals(qualifiedName) && type.policies().size() == 1 && type.policies().getFirst().isEvaluable()).ifPresent(type -> {
                        AresPolicyAnnotations.Annotation annotation = type.policies().getFirst();
                        Map<String, String> methodPolicyPaths = new LinkedHashMap<>();
                        Set<String> defaultMethods = new LinkedHashSet<>();
                        Set<String> switchedOff = new LinkedHashSet<>();
                        type.methodPolicies().forEach((method, methodAnnotation) -> {
                            if (!methodAnnotation.isEvaluable() || !methodAnnotation.isActivated()) {
                                switchedOff.add(method);
                            }
                            else {
                                methodAnnotation.policyPath().filter(path -> !path.isBlank()).ifPresentOrElse(path -> methodPolicyPaths.put(method, path),
                                        () -> defaultMethods.add(method));
                            }
                        });
                        // Like an annotation of a method, an empty path, e.g. @Policy(value = ""), names no policy file: the class uses the strict default policy.
                        String classPolicyPath = annotation.policyPath().filter(path -> !path.isBlank()).orElse(null);
                        testClasses.put(qualifiedName,
                                new TestClass(qualifiedName, sourcePath, annotation.isActivated(), classPolicyPath, Map.copyOf(methodPolicyPaths), Set.copyOf(defaultMethods),
                                        Set.copyOf(switchedOff), AresTestCases.overloadedTestCases(source), AresTestCases.testCases(source),
                                        type.superclass().stream().anyMatch(testClassPaths::containsKey)));
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
     * @return whether the entry names one of its test cases whose annotation switches the sandbox off
     */
    static boolean isSwitchedOffEntry(TestClass testClass, String entry) {
        return isMethodEntryIn(testClass.switchedOff(), entry);
    }

    /**
     * @param testClass a test class
     * @param entry     an entry of the class
     * @return whether the entry names a test case that more than one test method of the class has
     */
    static boolean isOverloadedEntry(TestClass testClass, String entry) {
        return isMethodEntryIn(testClass.overloaded(), entry);
    }

    private static boolean isMethodEntryIn(Set<String> testCases, String entry) {
        int separator = entry.indexOf(METHOD_SEPARATOR);
        return separator >= 0 && testCases.contains(entry.substring(separator + 1));
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

    /**
     * @param javaSources the Java sources of the repository by their path relative to its root, see
     *                        {@link SecurityPolicyRepositoryFiles#isJavaSourcePath(String, java.util.function.Predicate)}
     * @return the path of the source file of each test class, by the fully qualified name of the class, sorted by name
     */
    static Map<String, String> testClassPaths(Map<String, String> javaSources) {
        List<AresTestSources.Summary> summaries = javaSources.entrySet().stream().map(source -> AresTestSources.analyze(source.getKey(), source.getValue())).toList();
        Set<String> testClassNames = SecurityPolicyRepositoryFiles.testClasses(summaries).stream().map(AresTestSources.TypeDeclaration::qualifiedName).collect(Collectors.toSet());
        Map<String, String> paths = new TreeMap<>();
        for (AresTestSources.Summary summary : summaries) {
            summary.topLevelTypes().stream().filter(type -> testClassNames.contains(type.qualifiedName())).forEach(type -> paths.put(type.qualifiedName(), summary.path()));
        }
        return paths;
    }

    /**
     * Rewrites the {@code @Policy} annotations of test classes so that their test cases use the given policy files, see
     * {@link AresPolicyAnnotations#assign(String, String, String, Map, Set)}.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param testClasses    the test classes with the annotations they should have
     * @return the number of files that changed
     */
    static int assignTestCases(Path repositoryRoot, Collection<TestClass> testClasses) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        int changedFiles = 0;
        for (TestClass testClass : testClasses) {
            Path javaFile = root.resolve(testClass.sourcePath()).normalize();
            if (!javaFile.startsWith(root) || !Files.isRegularFile(javaFile, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            String content = SecurityPolicyRepositoryFiles.readFile(javaFile);
            if (SecurityPolicyRepositoryFiles.writeIfChanged(javaFile, content,
                    AresPolicyAnnotations.assign(testClass.sourcePath(), content, testClass.classPolicyPath(), testClass.methodPolicyPaths(), testClass.keptMethods()))) {
                changedFiles++;
            }
        }
        return changedFiles;
    }
}
