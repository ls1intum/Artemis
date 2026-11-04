package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * The tests repository's Ares 2 policy is server-owned: its permissions and supervised package stay exactly as seeded, and the server alone writes its test-class list from the
 * instructor test sources before every build. No agent edit is needed or accepted.
 * <p>
 * One permission is derived as well: when the reference solution contains student-owned JUnit test classes (an exercise in which students write tests), their JUnit import is
 * permitted, because Ares rejects it in supervised code otherwise. It is derived from the reference solution being generated, never from a student submission.
 */
final class AresSecurityPolicy {

    static final String PATH = "SecurityPolicy.yaml";

    private static final Pattern TEST_CLASSES = Pattern.compile("(?m)^  theFollowingClassesAreTestClasses:\\n((?:    - \\\"[A-Za-z_$][\\w.$]*\\\"\\n)*)");

    private static final Pattern SOURCE = Pattern.compile("^(?:test|behavior/test|structural/test)/([A-Za-z_$][\\w$/]*)\\.java$");

    /** The package student-written tests import; its permission is the only one ever derived. */
    static final String JUNIT_PACKAGE = "org.junit.jupiter";

    private static final Pattern PACKAGE_IMPORTS = Pattern.compile("(?m)^([ \\t]*)regardingPackageImports:[ \\t]*(\\[[ \\t]*\\])?[ \\t]*\\n");

    private static final Pattern JUNIT_IMPORT_PERMISSION = Pattern.compile("(?m)^[ \\t]*-[ \\t]*importTheFollowingPackage:[ \\t]*\"" + Pattern.quote(JUNIT_PACKAGE) + "\"[ \\t]*$");

    private AresSecurityPolicy() {
    }

    /**
     * Derives the policy the server writes: the seeded policy with its test-class list replaced by the instructor test source classes and, when the reference solution contains
     * student-owned JUnit tests, the JUnit import permitted. All other seeded bytes stay untouched.
     *
     * @param seedPolicy    the policy captured before authoring, or {@code null} when the exercise has none
     * @param testsFiles    the tests repository whose instructor test sources are registered; assignment sources never enter this map
     * @param solutionFiles the reference solution repository, which decides only whether student-written JUnit tests need their import permitted
     * @return the derived policy, or {@code null} without a seeded policy
     * @throws IllegalStateException when the seeded test-class list or package-import list cannot be inspected safely
     */
    static @Nullable String derive(@Nullable String seedPolicy, Map<String, String> testsFiles, Map<String, String> solutionFiles) {
        if (seedPolicy == null) {
            return null;
        }
        Matcher classes = TEST_CLASSES.matcher(normalized(seedPolicy));
        if (!classes.find()) {
            throw new IllegalStateException("The seeded Ares 2 test-class list cannot be inspected safely");
        }
        String list = testClasses(testsFiles).stream().map(name -> "    - \"" + name + "\"\n").collect(Collectors.joining());
        String policy = classes.replaceFirst(Matcher.quoteReplacement("  theFollowingClassesAreTestClasses:\n" + list));
        return hasStudentOwnedJUnitTests(solutionFiles) ? permitJUnitImport(policy) : policy;
    }

    /** Re-registers the test classes of an already server-derived policy, for verifier probes that add a trusted test source to the captured candidate. */
    static @Nullable String registerTestClasses(Map<String, String> tests) {
        // The candidate's policy is already server-derived, so a permitted JUnit import stays as it is.
        return derive(tests.get(PATH), tests, Map.of());
    }

    /** Whether a student source of the reference solution declares a JUnit test method, i.e. the exercise has students write tests. */
    static boolean hasStudentOwnedJUnitTests(Map<String, String> solutionFiles) {
        return solutionFiles.entrySet().stream().filter(file -> file.getKey().startsWith("src/") && file.getKey().endsWith(".java")).anyMatch(file -> {
            var summary = JavaSourceInspector.javaTestAnnotationSummary(file.getValue());
            return summary.hasTestMethods() && !summary.unsupportedAnnotationSyntax();
        });
    }

    private static String permitJUnitImport(String policy) {
        if (JUNIT_IMPORT_PERMISSION.matcher(policy).find()) {
            return policy;
        }
        Matcher imports = PACKAGE_IMPORTS.matcher(policy);
        if (!imports.find() || PACKAGE_IMPORTS.matcher(policy.substring(imports.end())).find()) {
            throw new IllegalStateException("The seeded Ares 2 package-import list cannot be inspected safely");
        }
        String indent = imports.group(1);
        String permission = indent + "  - importTheFollowingPackage: \"" + JUNIT_PACKAGE + "\"\n";
        return policy.substring(0, imports.start()) + indent + "regardingPackageImports:\n" + permission + policy.substring(imports.end());
    }

    /**
     * Rejects a produced policy that differs from the one the server derives. Without a seed (a harness-free or legacy snapshot) the produced policy's own permissions are taken as
     * the base, so its test-class list must still match the instructor test sources exactly.
     *
     * @param seed             the tests repository before authoring
     * @param produced         the produced tests repository
     * @param producedSolution the produced reference solution repository
     * @return rejection reasons, empty when the produced policy is exactly the server-derived one
     */
    static List<String> reasons(Map<String, String> seed, Map<String, String> produced, Map<String, String> producedSolution) {
        String policy = produced.get(PATH);
        if (policy == null) {
            return List.of("Ares 2 tests must keep the explicit SecurityPolicy.yaml; the default policy must not derive exemptions from student code.");
        }
        String seedPolicy = seed.get(PATH);
        String expected;
        try {
            expected = derive(seedPolicy == null ? policy : seedPolicy, produced, producedSolution);
        }
        catch (IllegalStateException exception) {
            return List.of("SecurityPolicy.yaml cannot be inspected safely (" + exception.getMessage()
                    + "): keep theFollowingClassesAreTestClasses and regardingPackageImports as block lists of double-quoted values.");
        }
        if (!normalized(policy).equals(expected)) {
            return List.of("tests/SecurityPolicy.yaml differs from the policy Artemis derives: it keeps the seeded permissions and supervised package unchanged"
                    + (hasStudentOwnedJUnitTests(producedSolution) ? ", permits the JUnit import of the student-written tests," : "")
                    + " and lists exactly the instructor test source classes (" + String.join(", ", testClasses(produced))
                    + "). Artemis writes this file before every build; restore it and do not edit it.");
        }
        return List.of();
    }

    private static String normalized(String policy) {
        return policy.replace("\r\n", "\n");
    }

    private static TreeSet<String> testClasses(Map<String, String> produced) {
        TreeSet<String> expected = new TreeSet<>();
        for (String path : produced.keySet()) {
            Matcher source = SOURCE.matcher(path);
            if (source.matches()) {
                expected.add(source.group(1).replace('/', '.'));
            }
        }
        return expected;
    }

}
