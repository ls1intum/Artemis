package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;

/**
 * Turns candidate {@link ContractWitness}es into validated ones by running them against the reference solution. Only the pure pieces live here so they are unit-testable without
 * Docker; the sandbox half (write the probe, build the solution, remove the probe) belongs to the caller that already owns a session.
 */
public final class ContractWitnessProbe {

    public static final String PROBE_CLASS_NAME = "HyperionContractWitnessProbeTest";

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+[^;]+;", Pattern.MULTILINE);

    private static final Pattern IMPORT_DECLARATION = Pattern.compile("^\\s*import\\s+[^;]+;", Pattern.MULTILINE);

    private ContractWitnessProbe() {
    }

    /**
     * Builds one compilable probe class carrying every witness method. The package and imports are lifted from a test the agent produced rather than assembled from a fixed list,
     * so the probe matches whichever assertion library and JUnit version that exercise uses. Class-level harness annotations are deliberately NOT copied: they are the graded
     * suite's contract with the production grader, and this throwaway must never look like a graded test.
     *
     * @param existingTestSource one graded test source from the same repository, used only as the source of the package and import declarations
     * @param witnesses          the candidate witnesses, each contributing one method
     * @return the complete probe source, or empty when there is nothing to build
     */
    public static String buildProbeSource(String existingTestSource, List<ContractWitness> witnesses) {
        if (witnesses.isEmpty()) {
            return "";
        }
        StringBuilder source = new StringBuilder();
        Matcher packageMatcher = PACKAGE_DECLARATION.matcher(existingTestSource);
        if (packageMatcher.find()) {
            source.append(packageMatcher.group().strip()).append("\n\n");
        }
        Set<String> imports = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT_DECLARATION.matcher(existingTestSource);
        while (importMatcher.find()) {
            imports.add(importMatcher.group().strip());
        }
        imports.forEach(declaration -> source.append(declaration).append("\n"));
        if (!imports.isEmpty()) {
            source.append("\n");
        }
        source.append("class ").append(PROBE_CLASS_NAME).append(" {\n");
        for (ContractWitness witness : witnesses) {
            source.append("\n").append(witness.code().strip().indent(4));
        }
        return source.append("}\n").toString();
    }

    /**
     * Decides which witnesses the build validated: a witness must appear among the tests the build REPORTED RUNNING and must not appear among its failures. Absence from the
     * failure list alone is satisfied by a witness the runner never discovered, one disabled by an assumption, or a whole probe class that failed to compile while the graded
     * tests still ran and made the build look healthy.
     *
     * @param executedTestNames the names the build reported running (from the same parsed report production grading uses)
     * @param failedTestNames   the names the build reported as failing
     * @param witnesses         the candidates that were written into the probe
     * @return the witnesses that demonstrably ran and passed against the reference solution
     */
    public static List<ContractWitness> validated(List<String> executedTestNames, List<String> failedTestNames, List<ContractWitness> witnesses) {
        Set<String> executed = bareNames(executedTestNames);
        Set<String> failed = bareNames(failedTestNames);
        List<ContractWitness> validated = new ArrayList<>();
        for (ContractWitness witness : witnesses) {
            if (executed.contains(witness.testName()) && !failed.contains(witness.testName())) {
                validated.add(witness);
            }
        }
        return List.copyOf(validated);
    }

    /**
     * Keeps only reference-solution witnesses that demonstrably execute and fail against the starter. Passing the solution proves the proposed outcome belongs to the selected
     * contract; failing at the template's student seam proves the witness is executable feedback rather than a vacuous assertion or given-support check.
     *
     * @param solutionValidated witnesses already observed passing against the reference solution
     * @param templateTestNames tests the template build reported executing
     * @param templateFailures  tests the template build reported failing
     * @return witnesses that distinguish the reference solution from the starter
     */
    public static List<ContractWitness> discriminating(List<ContractWitness> solutionValidated, List<String> templateTestNames, List<String> templateFailures) {
        Set<String> executed = bareNames(templateTestNames);
        Set<String> failed = bareNames(templateFailures);
        return solutionValidated.stream().filter(witness -> executed.contains(witness.testName()) && failed.contains(witness.testName())).toList();
    }

    static boolean executed(ContractWitness witness, List<String> testNames) {
        return bareNames(testNames).contains(witness.testName());
    }

    static boolean failed(ContractWitness witness, List<String> failedTestNames) {
        return bareNames(failedTestNames).contains(witness.testName());
    }

    static String failureDiagnostic(ContractWitness witness, BuildSummary summary) {
        return summary.failureEvidence().stream().filter(evidence -> bareNames(List.of(evidence.testName())).contains(witness.testName()))
                .map(AgentVerifyReport.TestFailureEvidence::message).filter(message -> !message.isBlank()).findFirst().orElse(summary.buildDiagnostic());
    }

    static boolean collidesWithExistingTest(ContractWitness witness, Map<String, String> testSources) {
        Pattern declaration = Pattern.compile("\\bvoid\\s+" + Pattern.quote(witness.testName()) + "\\s*\\(");
        return testSources.values().stream().anyMatch(source -> declaration.matcher(source).find());
    }

    /** Report forms differ per framework ({@code testFoo}, {@code testFoo()}, {@code ClassName.testFoo}), so matching on the bare name keeps attribution stable. */
    private static Set<String> bareNames(@Nullable List<String> reportedNames) {
        Set<String> bare = new LinkedHashSet<>();
        if (reportedNames == null) {
            return bare;
        }
        for (String reported : reportedNames) {
            if (reported == null) {
                continue;
            }
            String name = reported;
            int parenthesis = name.indexOf('(');
            if (parenthesis >= 0) {
                name = name.substring(0, parenthesis);
            }
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
            bare.add(name.strip());
        }
        return bare;
    }

    /**
     * Places the probe alongside the graded test it borrowed its package from, so the build discovers it by the same convention.
     *
     * @param existingTestPath  the workspace-relative path of that graded test
     * @param existingFilePaths every path already present in the tests repository
     * @return the probe's workspace-relative path, or {@code null} when the path has no directory or that name is already taken
     */
    public static @Nullable String probePath(String existingTestPath, Set<String> existingFilePaths) {
        int lastSlash = existingTestPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return null;
        }
        String path = existingTestPath.substring(0, lastSlash + 1) + PROBE_CLASS_NAME + ".java";
        // The name is distinctive, not reserved: overwriting a generated test of the same name would destroy graded work, and removing the probe would then delete it.
        return existingFilePaths.contains(path) ? null : path;
    }

    /**
     * Finds a normal assertion-based Java test whose package and imports a throwaway probe can safely reuse. Structural {@code @TestFactory} harnesses are deliberately excluded:
     * they sort before most behavioral tests but do not import {@code @Test}, assertions, or the domain helpers a model-authored witness is instructed to reuse.
     */
    static Optional<Map.Entry<String, String>> host(Map<String, String> testFiles) {
        return testFiles.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(".java") && !ExerciseIntegrityGate.isHarnessFile(entry.getKey()) && entry.getValue() != null
                        && entry.getValue().contains("package ") && entry.getValue().contains("@Test") && !entry.getValue().contains("@TestFactory")
                        && entry.getValue().matches("(?s).*\\b(assert\\w*|verify|expect)\\s*\\(.*"))
                .min(Map.Entry.comparingByKey());
    }
}
