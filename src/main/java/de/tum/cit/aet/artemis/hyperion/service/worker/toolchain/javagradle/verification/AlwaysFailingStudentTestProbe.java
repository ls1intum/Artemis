package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/** Detects credit awarded to unconditional failures in a pure, single-class test-body task. */
final class AlwaysFailingStudentTestProbe {

    private AlwaysFailingStudentTestProbe() {
    }

    static List<SemanticMutantOutcome> evaluate(Map<String, String> templateFiles, Map<String, String> solutionFiles, @Nullable String testPlanJson,
            SemanticMutantExecution.ProbeRunner runner) {
        List<Map.Entry<String, String>> changed = solutionFiles.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("src/") && entry.getKey().endsWith(".java") && !entry.getValue().equals(templateFiles.get(entry.getKey()))).toList();
        // With other learner-owned code, a passing criterion may legitimately assess that other work.
        if (changed.size() != 1) {
            return List.of();
        }
        var file = changed.getFirst();
        Optional<String> replacement = JavaSourceInspector.alwaysFailingStudentTests(file.getValue(), templateFiles.getOrDefault(file.getKey(), ""));
        if (replacement.isEmpty()) {
            return List.of();
        }
        SemanticMutant mutant = SemanticMutant.alwaysFailingStudentTests(file.getKey(), file.getValue(), replacement.get());
        BuildSummary baseline = runner.run(null, null);
        Predicate<String> graded = OutputOnlyMainProbe.gradedTest(testPlanJson);
        List<String> expected = baseline.testNames().stream().filter(graded).toList();
        if (baseline.timedOut() || baseline.exitCode() != 0 || baseline.failures() != 0 || !baseline.testFailedNames().isEmpty() || baseline.tests() == 0 || expected.isEmpty()) {
            return List.of(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
        }
        BuildSummary result = runner.run(mutant, null);
        if (result.timedOut() || !result.testNames().containsAll(expected) || result.exitCode() != 0 && result.failures() == 0
                || result.failures() > 0 && result.testFailedNames().isEmpty() || !result.testNames().containsAll(result.testFailedNames())) {
            return List.of(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE, result.buildDiagnostic()));
        }
        List<String> accepted = expected.stream().filter(test -> !result.testFailedNames().contains(test)).toList();
        // This probe asks whether invalid tests earn ANY credit, rather than whether the entire suite passes.
        if (!accepted.isEmpty()) {
            String diagnostic = accepted.size() + " weighted check(s) still pass when every assigned learner test always fails: " + accepted.stream().limit(8).toList();
            ContractWitness description = new ContractWitness(mutant.ruleId(), "", "", diagnostic);
            SemanticMutant observed = new SemanticMutant(mutant.ruleId(), mutant.solutionPath(), mutant.originalSolutionSource(), mutant.mutantSource(), description, null,
                    mutant.evidence());
            return List.of(new SemanticMutantOutcome(observed, Disposition.SURVIVED_GRADED_SUITE, diagnostic));
        }
        return List.of(new SemanticMutantOutcome(mutant, Disposition.KILLED_BY_GRADED_SUITE));
    }
}
