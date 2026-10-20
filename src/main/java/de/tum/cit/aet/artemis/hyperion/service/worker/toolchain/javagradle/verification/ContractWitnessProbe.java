package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.runtime.verification.JavaSourceText;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;

/** Builds witness probes and interprets their test reports; the caller owns sandbox execution and cleanup. */
public final class ContractWitnessProbe {

    private static final Pattern ASSERTION_CALL = Pattern.compile("(?s).*\\b(assert\\w*|verify|expect)\\s*\\(.*");

    public static final String PROBE_CLASS_NAME = "HyperionContractWitnessProbeTest";

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+[^;]+;", Pattern.MULTILINE);

    private static final Pattern IMPORT_DECLARATION = Pattern.compile("^\\s*import\\s+[^;]+;", Pattern.MULTILINE);

    private static final Pattern METHOD_DECLARATION = Pattern.compile("\\bvoid\\s+([a-zA-Z_$][a-zA-Z\\d_$]*)\\s*\\(");

    private ContractWitnessProbe() {
    }

    /**
     * Combines witness methods with an existing test's package and imports, using the same Ares restrictions as instructor tests. The caller restores the candidate after probing,
     * so these temporary tests never enter the saved grading suite.
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
        source.append("@de.tum.cit.ase.ares.api.jupiter.Public\n").append("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\n")
                .append("@de.tum.cit.ase.ares.api.StrictTimeout(").append(JavaSourceInspector.MAX_STRICT_TIMEOUT_SECONDS).append(")\n")
                .append("@org.junit.jupiter.api.DisplayNameGeneration(org.junit.jupiter.api.DisplayNameGenerator.Simple.class)\n").append("class ").append(PROBE_CLASS_NAME)
                .append(" {\n");
        for (ContractWitness witness : witnesses) {
            source.append("\n").append(witness.code().strip().indent(4));
        }
        return source.append("}\n").toString();
    }

    /** Builds the temporary source and policy overlay without changing the candidate's resource permissions. */
    static Map<String, String> workspaceOverlay(Map<String, String> testsFiles, String probePath, String probeSource) {
        Map<String, String> augmentedTests = new LinkedHashMap<>(testsFiles);
        if (augmentedTests.putIfAbsent(probePath, probeSource) != null) {
            throw new IllegalArgumentException("The contract-witness probe would overwrite an existing test");
        }
        String policy = AresSecurityPolicy.registerTestClasses(augmentedTests);
        if (policy == null) {
            throw new IllegalArgumentException("The contract-witness probe requires the candidate's Ares 2 security policy");
        }
        return Map.of("tests/" + probePath, probeSource, "tests/" + AresSecurityPolicy.PATH, policy);
    }

    /**
     * Requires each witness to appear in the executed-test report and not in the failure report. An undiscovered witness is not a passing witness.
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
     * Keeps solution-passing witnesses that also execute and fail against the template. This establishes a behavioral difference, not correctness against the specification.
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
        return testSources.values().stream().anyMatch(source -> declaration.matcher(JavaSourceText.maskCommentsAndLiterals(source)).find());
    }

    /** Renames only an adopted witness's method declaration for unambiguous temporary probe attribution. */
    static ContractWitness forExecution(ContractWitness witness, Map<String, String> testSources) {
        if (!collidesWithExistingTest(witness, testSources)) {
            return witness;
        }
        String base = witness.testName() + "HyperionProbe";
        String name = base;
        int suffix = 2;
        while (collidesWithExistingTest(new ContractWitness(witness.ruleId(), name, witness.code(), witness.wrongBehavior()), testSources)) {
            name = base + suffix++;
        }
        Matcher declaration = METHOD_DECLARATION.matcher(JavaSourceText.maskCommentsAndLiterals(witness.code()));
        int start = -1;
        int end = -1;
        while (declaration.find()) {
            if (declaration.group(1).equals(witness.testName())) {
                if (start >= 0) {
                    throw new IllegalArgumentException("The witness has multiple matching method declarations.");
                }
                start = declaration.start(1);
                end = declaration.end(1);
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("The witness has no matching method declaration.");
        }
        String code = witness.code().substring(0, start) + name + witness.code().substring(end);
        return new ContractWitness(witness.ruleId(), name, code, witness.wrongBehavior());
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

    /** Selects an assertion-based Java test as the import/package source; excludes structural test factories. */
    static Optional<Map.Entry<String, String>> host(Map<String, String> testFiles) {
        return testFiles.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(".java") && !ExerciseIntegrityGate.isHarnessFile(entry.getKey()) && entry.getValue() != null
                        && entry.getValue().contains("package ") && entry.getValue().contains("@Test") && !entry.getValue().contains("@TestFactory")
                        && ASSERTION_CALL.matcher(entry.getValue()).matches())
                .min(Map.Entry.comparingByKey());
    }
}
