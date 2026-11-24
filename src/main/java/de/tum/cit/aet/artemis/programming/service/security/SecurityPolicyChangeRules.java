package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyChangeDTO;

/**
 * The rules a change of the {@link SecurityPolicyEditorService} has to follow: valid policy paths and documents, assignments that Ares can express with its
 * {@code @Policy} annotations, and the errors that refuse a change.
 */
final class SecurityPolicyChangeRules {

    private static final Set<String> POLICY_FILE_EXTENSIONS = Set.of(".yaml", ".yml");

    static final String THE_POLICY = "The policy ";

    private static final String THE_TEST_CLASS = "The test class ";

    /** The parameter of an error message that names a test class. */
    private static final String TEST_CLASS = "testClass";

    private SecurityPolicyChangeRules() {
    }

    static void requireValidChange(SecurityPolicyChangeDTO change) {
        Set<String> paths = new HashSet<>();
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            String path = requireValidPolicy(policy);
            if (!paths.add(path)) {
                throw listedTwice(path);
            }
        }
        for (String removedPath : change.removedPolicyPaths()) {
            String path = requireValidPolicyPath(removedPath);
            if (paths.contains(path)) {
                throw listedTwice(path);
            }
        }
        // A policy file cannot be the directory of another one, e.g. policies.yaml and policies.yaml/Stress.yaml.
        for (String path : paths) {
            if (paths.stream().anyMatch(other -> other.startsWith(path + "/"))) {
                throw invalidPath(path, "is also the directory of another policy.");
            }
        }
    }

    /**
     * @return the valid path of the changed policy
     */
    private static String requireValidPolicy(SecurityPolicyChangeDTO.PolicyChangeDTO policy) {
        if (policy == null || policy.document() == null) {
            throw new BadRequestAlertException("A changed policy has no content.", ENTITY_NAME, "securityPolicyNoChange");
        }
        if (policy.assignedTests().stream().anyMatch(Objects::isNull)) {
            throw new BadRequestAlertException("A changed policy lists a test without a name.", ENTITY_NAME, "securityPolicyNoChange");
        }
        String path = requireValidPolicyPath(policy.document().policyPath());
        List<String> problems = SecurityPolicyDocuments.validate(policy.document());
        if (!problems.isEmpty()) {
            throw refused(THE_POLICY + path + " is not valid: " + String.join(" ", problems), "securityPolicyInvalid", Map.of("path", path, "details", String.join(" ", problems)));
        }
        return path;
    }

    /**
     * A policy path must be valid for Ares, end with {@code .yaml} or {@code .yml}, and be writable as a plain string literal into a {@code @Policy}
     * annotation.
     */
    static String requireValidPolicyPath(@Nullable String policyPath) {
        Optional<String> path = Optional.ofNullable(policyPath).flatMap(SecurityPolicyRepositoryFiles::validatePolicyPath)
                .filter(validPath -> POLICY_FILE_EXTENSIONS.stream().anyMatch(validPath::endsWith))
                .filter(validPath -> validPath.indexOf('"') < 0 && validPath.indexOf('\\') < 0 && validPath.chars().noneMatch(Character::isISOControl));
        return path.orElseThrow(() -> invalidPath(String.valueOf(policyPath), "is not valid."));
    }

    static String requireExistingPolicy(Map<String, SecurityFrameworkState.PolicyFile> existing, String path) {
        if (!existing.containsKey(path)) {
            throw refused("No test uses the policy " + path + " in the tests repository.", "securityPolicyNotFound", Map.of("path", path));
        }
        return path;
    }

    /**
     * @param deleted the policy files the change deletes, which no longer block a path below them
     * @return whether a directory of the path is a file of the checkout instead, e.g. {@code notes.txt} in {@code notes.txt/SecurityPolicy.yaml}
     */
    static boolean liesBelowAFile(Path root, String path, Set<String> deleted) {
        for (Path parent = root.resolve(path).getParent(); parent != null && parent.startsWith(root) && !parent.equals(root); parent = parent.getParent()) {
            String relative = root.relativize(parent).toString().replace('\\', '/');
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) && !deleted.contains(relative)) {
                return true;
            }
        }
        return false;
    }

    static BadRequestAlertException fileExists(String path) {
        return refused("The file " + path + " already exists in the tests repository.", "securityPolicyFileExists", Map.of("path", path));
    }

    static boolean holdsPolicy(SecurityPolicyRepositoryFiles.PolicyFileContent content) {
        return content.problem() == null && content.content() != null && SecurityPolicyYaml.isPolicyShaped(content.content());
    }

    static boolean usesInstrumentation(@Nullable String languageConfiguration) {
        return languageConfiguration != null && languageConfiguration.endsWith("_INSTRUMENTATION");
    }

    /**
     * The policy of every assigned test after the change: the policies the change does not touch keep their tests, and each listed policy gets the tests it
     * lists. A test can only use one policy, and a whole test class cannot be assigned together with one of its test cases.
     *
     * @param touched the paths in the repository of the policies the change rewrites, renames or deletes
     * @return the policy path by entry, see {@link SecurityTestAssignments}
     */
    static Map<String, String> requestedEntries(SecurityPolicyChangeDTO change, Map<String, SecurityTestAssignments.TestClass> testClasses, Set<String> touched) {
        Map<String, String> requested = new LinkedHashMap<>();
        for (SecurityTestAssignments.TestClass testClass : testClasses.values()) {
            testClass.entries().forEach((entry, path) -> {
                if (!touched.contains(path)) {
                    requested.put(entry, path);
                }
            });
        }
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            String path = requireValidPolicyPath(policy.document().policyPath());
            Set<String> declared = new HashSet<>(policy.document().testClasses());
            for (String entry : policy.assignedTests()) {
                requireAssignable(testClasses, entry, path, declared);
                String other = requested.put(entry, path);
                if (other != null && !other.equals(path)) {
                    throw assignedTwice(entry, other, path);
                }
            }
        }
        requireNoTestCaseOfAnAssignedClass(requested);
        return requested;
    }

    /**
     * @param path     the policy the test is assigned to
     * @param declared the test classes the policy declares
     */
    private static void requireAssignable(Map<String, SecurityTestAssignments.TestClass> testClasses, String entry, String path, Set<String> declared) {
        SecurityTestAssignments.TestClass testClass = testClasses.get(SecurityTestAssignments.testClassOf(entry));
        if (testClass == null || !SecurityTestAssignments.isValidEntry(testClass, entry)) {
            throw refused("The tests repository has no test " + entry + ".", "securityPolicyUnknownTest", Map.of("test", entry));
        }
        if (!testClass.active()) {
            throw refused(THE_TEST_CLASS + testClass.qualifiedName() + " runs without the sandbox, so its tests cannot use a policy.", "securityPolicyTestSwitchedOff",
                    Map.of(TEST_CLASS, testClass.qualifiedName()));
        }
        if (SecurityTestAssignments.isSwitchedOffEntry(testClass, entry)) {
            throw refused("The test " + entry + " runs without the sandbox, so it cannot use a policy.", "securityPolicyTestCaseSwitchedOff", Map.of("test", entry));
        }
        if (SecurityTestAssignments.isOverloadedEntry(testClass, entry)) {
            throw refused("More than one test method is called " + entry + ", so they can only use a policy with their whole class.", "securityPolicyTestCaseOverloaded",
                    Map.of("test", entry));
        }
        if (!declared.contains(testClass.qualifiedName())) {
            throw refused(THE_POLICY + path + " does not declare the test class " + testClass.qualifiedName() + " of its test " + entry + ".", "securityPolicyTestClassNotDeclared",
                    Map.of("path", path, TEST_CLASS, testClass.qualifiedName()));
        }
    }

    /**
     * A whole test class cannot be assigned together with one of its test cases.
     */
    private static void requireNoTestCaseOfAnAssignedClass(Map<String, String> requested) {
        Map<String, String> wholeClasses = new HashMap<>();
        requested.forEach((entry, path) -> {
            if (entry.indexOf(SecurityTestAssignments.METHOD_SEPARATOR) < 0) {
                wholeClasses.put(entry, path);
            }
        });
        requested.forEach((entry, path) -> {
            String wholeClass = wholeClasses.get(SecurityTestAssignments.testClassOf(entry));
            if (wholeClass != null && !entry.equals(SecurityTestAssignments.testClassOf(entry))) {
                throw assignedTwice(entry, wholeClass, path);
            }
        });
    }

    /**
     * The test classes whose assignments change, with their new assignments. A class annotation that points to a renamed or deleted file changes too, even if
     * none of the test cases used it. Refuses a change that would drop a policy of test methods the editor cannot assign one by one.
     *
     * @param requested the policy path of every assigned test after the change, by entry, see {@link #requestedEntries}
     * @param vacated   the paths the change frees: deleted policies and the old paths of renamed ones
     */
    static List<SecurityTestAssignments.TestClass> reassignedTestClasses(Map<String, SecurityTestAssignments.TestClass> testClasses, Map<String, String> requested,
            Set<String> vacated) {
        List<SecurityTestAssignments.TestClass> reassigned = new ArrayList<>();
        for (SecurityTestAssignments.TestClass testClass : testClasses.values()) {
            Map<String, String> entries = new LinkedHashMap<>();
            requested.forEach((entry, path) -> {
                if (SecurityTestAssignments.testClassOf(entry).equals(testClass.qualifiedName())) {
                    entries.put(entry, path);
                }
            });
            SecurityTestAssignments.TestClass assigned = testClass.with(entries);
            boolean pointsToVacated = testClass.classPolicyPath() != null && vacated.contains(testClass.classPolicyPath());
            if (testClass.active() && (pointsToVacated || !assigned.entries().equals(testClass.entries()))) {
                requireKeptOverloadPolicy(testClass, assigned);
                requireKeptInheritedPolicy(testClass, assigned);
                requireKeptPolicyOfSubclasses(testClass, assigned);
                reassigned.add(assigned);
            }
        }
        return reassigned;
    }

    /**
     * The test methods a class inherits from another test class are not among its test cases, so they cannot be assigned one by one; they follow its class
     * annotation, which therefore must not lose its policy file unnoticed. Only a class the editor shows as assigned as a whole may be released completely;
     * releasing some of its test cases, or the last one the editor shows of a class assigned case by case, would release the inherited ones as well.
     */
    private static void requireKeptInheritedPolicy(SecurityTestAssignments.TestClass testClass, SecurityTestAssignments.TestClass assigned) {
        String classPolicyPath = testClass.classPolicyPath();
        boolean releasedAsAWhole = testClass.entries().containsKey(testClass.qualifiedName()) && assigned.methodPolicyPaths().isEmpty();
        if (!testClass.inheritsTests() || classPolicyPath == null || assigned.classPolicyPath() != null || releasedAsAWhole) {
            return;
        }
        throw refused(
                THE_TEST_CLASS + testClass.qualifiedName() + " inherits test methods that use the policy " + classPolicyPath
                        + " through its class annotation. Assign the whole class to a policy, or release it completely.",
                "securityPolicyInheritedTestsLosePolicy", Map.of(TEST_CLASS, testClass.qualifiedName(), "path", classPolicyPath));
    }

    /**
     * Ares applies the annotation of a test method also when a subclass runs the method it inherits, before the annotation of the subclass. Changing the
     * annotation of a test method that another test class runs without an override would therefore also change the policy of that subclass, which the
     * editor shows as assigned on its own. A method every subclass overrides runs with the annotations of the override, so its own may change, e.g. lose a
     * redundant policy path when the class is renamed as a whole.
     */
    private static void requireKeptPolicyOfSubclasses(SecurityTestAssignments.TestClass testClass, SecurityTestAssignments.TestClass assigned) {
        boolean keptForSubclasses = testClass.runBySubclasses().stream()
                .allMatch(method -> Objects.equals(assigned.methodPolicyPaths().get(method), testClass.methodPolicyPaths().get(method))
                        && assigned.defaultMethods().contains(method) == testClass.defaultMethods().contains(method));
        if (keptForSubclasses) {
            return;
        }
        throw refused("Other test classes extend " + testClass.qualifiedName() + " and run its test methods with their policy. Assign its whole test class to a policy instead.",
                "securityPolicyTestsOfSubclassesChange", Map.of(TEST_CLASS, testClass.qualifiedName()));
    }

    /**
     * Test methods that share a name cannot be assigned one by one; they follow the class annotation, which therefore must not lose its policy file under
     * them unnoticed, e.g. when the file is renamed while the class is assigned case by case.
     */
    private static void requireKeptOverloadPolicy(SecurityTestAssignments.TestClass testClass, SecurityTestAssignments.TestClass assigned) {
        String classPolicyPath = testClass.classPolicyPath();
        // A class assigned as a whole shows the overloads as part of it, so releasing it completely is what the instructor asked for; keeping only some of its
        // test cases would release the overloads as well, which the editor cannot show one by one.
        boolean releasedAsAWhole = testClass.entries().containsKey(testClass.qualifiedName()) && assigned.methodPolicyPaths().isEmpty();
        // The annotations are read by the name of the method, so whether every overload has one of its own is unknown; any of them may use the class policy.
        if (classPolicyPath == null || assigned.classPolicyPath() != null || releasedAsAWhole || testClass.overloaded().isEmpty()) {
            return;
        }
        String overload = testClass.overloaded().stream().sorted().findFirst().orElseThrow();
        throw refused(
                THE_TEST_CLASS + testClass.qualifiedName() + " has several test methods called " + overload + ", which can use the policy " + classPolicyPath
                        + " of the class only through the class annotation. Assign the whole class to a policy, or change these annotations in the repository.",
                "securityPolicyOverloadsLosePolicy", Map.of(TEST_CLASS, testClass.qualifiedName(), "test", overload, "path", classPolicyPath));
    }

    private static BadRequestAlertException assignedTwice(String test, String first, String second) {
        return refused("The test " + test + " is assigned to the policies " + first + " and " + second + ".", "securityPolicyTestAssignedTwice",
                Map.of("test", test, "first", first, "second", second));
    }

    static BadRequestAlertException versionIncompatible(@Nullable String frameworkVersion, List<String> problems) {
        return refused("The pinned Ares version " + frameworkVersion + " does not accept the policy: " + String.join("; ", problems), "securityPolicyVersionIncompatible",
                Map.of("version", Objects.requireNonNullElse(frameworkVersion, ""), "details", String.join("; ", problems)));
    }

    /**
     * @return the error that refuses a request; the client shows its translation with the parameters, so the message names the translation
     */
    static BadRequestAlertException listedTwice(String path) {
        return refused(THE_POLICY + path + " is listed twice.", "securityPolicyDuplicate", Map.of("path", path));
    }

    /**
     * @param problem what is wrong with the path, a sentence that follows it
     */
    static BadRequestAlertException invalidPath(String path, String problem) {
        return refused("The policy path " + path + " " + problem, "securityPolicyInvalidPath", Map.of("path", path));
    }

    static BadRequestAlertException refused(String defaultMessage, String errorKey, Map<String, Object> params) {
        return new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, defaultMessage, ENTITY_NAME, errorKey, Map.of("message", "error." + errorKey, "params", params));
    }
}
