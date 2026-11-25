package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/** Classifies sandbox mutant builds; {@link DifferentialVerifier} remains the sole owner of executing and restoring the workspace. */
final class SemanticMutantExecution {

    @FunctionalInterface
    interface ProbeRunner {

        BuildSummary run(@Nullable SemanticMutant mutant, @Nullable Map<String, String> counterexample);
    }

    private SemanticMutantExecution() {
    }

    static List<SemanticMutant> validate(Map<String, String> testFiles, Map<String, String> solutionFiles, List<SemanticMutant> candidates, ProbeRunner runner) {
        return evaluate(testFiles, solutionFiles, candidates, runner).stream().filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE)
                .map(SemanticMutantOutcome::mutant).toList();
    }

    static List<SemanticMutantOutcome> evaluate(Map<String, String> testFiles, Map<String, String> solutionFiles, List<SemanticMutant> candidates, ProbeRunner runner) {
        Optional<Map.Entry<String, String>> host = ContractWitnessProbe.host(testFiles);
        if (host.isEmpty()) {
            return inconclusive(candidates);
        }
        String probePath = ContractWitnessProbe.probePath(host.get().getKey(), testFiles.keySet());
        if (probePath == null) {
            return inconclusive(candidates);
        }
        List<SemanticMutantOutcome> outcomes = new ArrayList<>();
        // The caller bounds the admitted proposals; every one it passes receives an outcome, so none is silently dropped.
        for (SemanticMutant mutant : candidates) {
            if (!mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath())) || declaresMethod(testFiles, mutant.counterexample().testName())) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
                continue;
            }
            String source = ContractWitnessProbe.buildProbeSource(host.get().getValue(), List.of(mutant.counterexample()));
            if (source.isBlank() || !ExerciseIntegrityGate.nondeterministicGradedTestReasons(Map.of(probePath, source)).isEmpty()
                    || !ExerciseIntegrityGate.gradingContextSniffingReasons(Map.of(), Map.of(mutant.solutionPath(), mutant.mutantSource())).isEmpty()) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
                continue;
            }
            BuildSummary existingSuite = runner.run(mutant, null);
            boolean killedByExistingSuite = !passed(existingSuite) && executedTestFailed(existingSuite);
            if (!passed(existingSuite) && !killedByExistingSuite) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
                continue;
            }
            Map<String, String> overlay = ContractWitnessProbe.workspaceOverlay(testFiles, probePath, source);
            BuildSummary pristine = runner.run(null, overlay);
            if (ContractWitnessProbe.executed(mutant.counterexample(), pristine.testNames()) && ContractWitnessProbe.failed(mutant.counterexample(), pristine.testFailedNames())
                    && !pristine.timedOut()) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.REFERENCE_TEST_FAILED, ContractWitnessProbe.failureDiagnostic(mutant.counterexample(), pristine)));
                continue;
            }
            if (ContractWitnessProbe.validated(pristine.testNames(), pristine.testFailedNames(), List.of(mutant.counterexample())).isEmpty()) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE, pristine.buildDiagnostic()));
                continue;
            }
            BuildSummary mutated = runner.run(mutant, overlay);
            if (!ContractWitnessProbe.discriminating(List.of(mutant.counterexample()), mutated.testNames(), mutated.testFailedNames()).isEmpty()) {
                outcomes.add(new SemanticMutantOutcome(mutant, killedByExistingSuite ? Disposition.KILLED_BY_GRADED_SUITE : Disposition.SURVIVED_GRADED_SUITE));
            }
            else {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
            }
        }
        return List.copyOf(outcomes);
    }

    static List<SemanticMutantOutcome> recheck(Map<String, String> solutionFiles, List<SemanticMutant> mutants, ProbeRunner runner) {
        return recheck(solutionFiles, mutants, testName -> true, runner);
    }

    /**
     * Rechecks mutants whose kill only counts when a test that earns credit fails.
     *
     * @param countedTests whether a failure of the executed test with this name kills the mutant; a failure of any other test, such as a zero-weight check of supplied
     *                         behavior, earns a student nothing and neither kills the mutant nor makes the build inconclusive
     */
    static List<SemanticMutantOutcome> recheck(Map<String, String> solutionFiles, List<SemanticMutant> mutants, Predicate<String> countedTests, ProbeRunner runner) {
        return recheck(solutionFiles, mutants, countedTests, runner, _ -> {
        });
    }

    static List<SemanticMutantOutcome> recheck(Map<String, String> solutionFiles, List<SemanticMutant> mutants, Predicate<String> countedTests, ProbeRunner runner,
            Consumer<SemanticMutantOutcome> completed) {
        List<SemanticMutantOutcome> outcomes = new ArrayList<>();
        if (mutants.isEmpty()) {
            return List.of();
        }
        BuildSummary baseline = null;
        for (SemanticMutant mutant : mutants) {
            if (!mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath()))) {
                // This exact replacement was proven only against another reference revision. It is neither a survivor nor a kill against the current candidate; the orchestration
                // review authors a fresh mutant against the new source instead of making an unexecuted stale probe block acceptance.
                var outcome = new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE);
                outcomes.add(outcome);
                completed.accept(outcome);
                continue;
            }
            if (baseline == null) {
                baseline = runner.run(null, null);
            }
            Set<String> expectedTests = baseline.testNames().stream().filter(countedTests).collect(Collectors.toUnmodifiableSet());
            if (!passed(baseline) || expectedTests.isEmpty()) {
                var outcome = new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE);
                outcomes.add(outcome);
                completed.accept(outcome);
                continue;
            }
            BuildSummary result = runner.run(mutant, null);
            // The repair may rename, strengthen, or replace the suggested counterexample. Because the candidate already passed mechanical verification immediately before this
            // probe, any test that executes and fails only after installing the mutant is outcome-level evidence that the suite now kills it. Compile failures execute no tests.
            var outcome = new SemanticMutantOutcome(mutant, result.testNames().containsAll(expectedTests) ? disposition(result, countedTests) : Disposition.INCONCLUSIVE);
            outcomes.add(outcome);
            completed.accept(outcome);
        }
        return List.copyOf(outcomes);
    }

    private static boolean declaresMethod(Map<String, String> testFiles, String methodName) {
        Pattern declaration = Pattern.compile("\\bvoid\\s+" + Pattern.quote(methodName) + "\\s*\\(");
        return testFiles.values().stream().filter(java.util.Objects::nonNull).anyMatch(source -> declaration.matcher(source).find());
    }

    private static boolean passed(BuildSummary summary) {
        return !summary.timedOut() && summary.exitCode() == 0 && summary.tests() > 0 && summary.failures() == 0;
    }

    private static boolean executedTestFailed(BuildSummary summary) {
        return executedTestFailed(summary, testName -> true);
    }

    private static Disposition disposition(BuildSummary result, Predicate<String> countedTests) {
        if (executedTestFailed(result, countedTests)) {
            return Disposition.KILLED_BY_GRADED_SUITE;
        }
        return passedCountedTests(result, countedTests) ? Disposition.SURVIVED_GRADED_SUITE : Disposition.INCONCLUSIVE;
    }

    private static boolean executedTestFailed(BuildSummary summary, Predicate<String> countedTests) {
        return !summary.timedOut() && summary.testFailedNames().stream().filter(countedTests).anyMatch(summary.testNames()::contains);
    }

    /** A build that ran tests and in which every failure, if any, was an executed test that does not count. */
    private static boolean passedCountedTests(BuildSummary summary, Predicate<String> countedTests) {
        return passed(summary) || (!summary.timedOut() && summary.tests() > 0 && summary.failures() > 0 && summary.testFailedNames().stream().noneMatch(countedTests)
                && summary.testNames().containsAll(summary.testFailedNames()));
    }

    private static List<SemanticMutantOutcome> inconclusive(List<SemanticMutant> candidates) {
        return candidates.stream().map(candidate -> new SemanticMutantOutcome(candidate, Disposition.INCONCLUSIVE)).toList();
    }
}
