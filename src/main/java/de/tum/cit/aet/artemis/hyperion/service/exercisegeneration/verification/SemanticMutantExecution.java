package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome.Disposition;

/** Pure decision logic around sandbox mutant builds; {@link DifferentialVerificationService} remains the sole owner of executing and restoring the workspace. */
final class SemanticMutantExecution {

    private static final int MAX_MUTANTS = 4;

    @FunctionalInterface
    interface ProbeRunner {

        BuildSummary run(@Nullable SemanticMutant mutant, Map.@Nullable Entry<String, String> counterexample);
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
        for (SemanticMutant mutant : candidates.stream().limit(MAX_MUTANTS).toList()) {
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
            BuildSummary pristine = runner.run(null, Map.entry(probePath, source));
            if (ContractWitnessProbe.validated(pristine.testNames(), pristine.testFailedNames(), List.of(mutant.counterexample())).isEmpty()) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
                continue;
            }
            BuildSummary mutated = runner.run(mutant, Map.entry(probePath, source));
            if (!ContractWitnessProbe.discriminating(List.of(mutant.counterexample()), mutated.testNames(), mutated.testFailedNames()).isEmpty()) {
                outcomes.add(new SemanticMutantOutcome(mutant, killedByExistingSuite ? Disposition.KILLED_BY_GRADED_SUITE : Disposition.SURVIVED_GRADED_SUITE));
            }
            else {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
            }
        }
        return List.copyOf(outcomes);
    }

    static List<SemanticMutant> surviving(Map<String, String> solutionFiles, List<SemanticMutant> mutants, ProbeRunner runner) {
        List<SemanticMutant> surviving = new ArrayList<>();
        for (SemanticMutant mutant : mutants.stream().limit(MAX_MUTANTS).toList()) {
            if (!mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath()))) {
                surviving.add(mutant);
                continue;
            }
            BuildSummary result = runner.run(mutant, null);
            // The repair may rename, strengthen, or replace the suggested counterexample. Because the candidate already passed mechanical verification immediately before this
            // probe, any test that executes and fails only after installing the mutant is outcome-level evidence that the suite now kills it. Compile failures execute no tests.
            if (result.testFailedNames().isEmpty() || result.testNames().stream().noneMatch(result.testFailedNames()::contains)) {
                surviving.add(mutant);
            }
        }
        return List.copyOf(surviving);
    }

    private static boolean declaresMethod(Map<String, String> testFiles, String methodName) {
        Pattern declaration = Pattern.compile("\\bvoid\\s+" + Pattern.quote(methodName) + "\\s*\\(");
        return testFiles.values().stream().filter(java.util.Objects::nonNull).anyMatch(source -> declaration.matcher(source).find());
    }

    private static boolean passed(BuildSummary summary) {
        return !summary.timedOut() && summary.exitCode() == 0 && summary.tests() > 0 && summary.failures() == 0;
    }

    private static boolean executedTestFailed(BuildSummary summary) {
        return !summary.timedOut() && !summary.testFailedNames().isEmpty() && summary.testNames().stream().anyMatch(summary.testFailedNames()::contains);
    }

    private static List<SemanticMutantOutcome> inconclusive(List<SemanticMutant> candidates) {
        return candidates.stream().limit(MAX_MUTANTS).map(candidate -> new SemanticMutantOutcome(candidate, Disposition.INCONCLUSIVE)).toList();
    }
}
