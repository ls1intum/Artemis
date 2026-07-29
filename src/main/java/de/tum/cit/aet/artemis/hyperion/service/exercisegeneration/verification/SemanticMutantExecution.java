package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;

/** Pure decision logic around sandbox mutant builds; {@link DifferentialVerificationService} remains the sole owner of executing and restoring the workspace. */
final class SemanticMutantExecution {

    @FunctionalInterface
    interface ProbeRunner {

        BuildSummary run(@Nullable SemanticMutant mutant, Map.@Nullable Entry<String, String> counterexample);
    }

    private SemanticMutantExecution() {
    }

    static List<SemanticMutant> validate(Map<String, String> testFiles, Map<String, String> solutionFiles, List<SemanticMutant> candidates, ProbeRunner runner) {
        Optional<Map.Entry<String, String>> host = ContractWitnessProbe.host(testFiles);
        if (host.isEmpty()) {
            return List.of();
        }
        String probePath = ContractWitnessProbe.probePath(host.get().getKey(), testFiles.keySet());
        if (probePath == null) {
            return List.of();
        }
        List<SemanticMutant> validated = new ArrayList<>();
        for (SemanticMutant mutant : candidates.stream().limit(2).toList()) {
            if (!mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath())) || declaresMethod(testFiles, mutant.counterexample().testName())) {
                continue;
            }
            String source = ContractWitnessProbe.buildProbeSource(host.get().getValue(), List.of(mutant.counterexample()));
            if (source.isBlank() || !ExerciseIntegrityGate.nondeterministicGradedTestReasons(Map.of(probePath, source)).isEmpty()
                    || !ExerciseIntegrityGate.gradingContextSniffingReasons(Map.of(), Map.of(mutant.solutionPath(), mutant.mutantSource())).isEmpty()
                    || !passed(runner.run(mutant, null))) {
                continue;
            }
            BuildSummary pristine = runner.run(null, Map.entry(probePath, source));
            if (ContractWitnessProbe.validated(pristine.testNames(), pristine.testFailedNames(), List.of(mutant.counterexample())).isEmpty()) {
                continue;
            }
            BuildSummary mutated = runner.run(mutant, Map.entry(probePath, source));
            if (!ContractWitnessProbe.discriminating(List.of(mutant.counterexample()), mutated.testNames(), mutated.testFailedNames()).isEmpty()) {
                validated.add(mutant);
            }
        }
        return List.copyOf(validated);
    }

    static List<SemanticMutant> surviving(Map<String, String> solutionFiles, List<SemanticMutant> mutants, ProbeRunner runner) {
        List<SemanticMutant> surviving = new ArrayList<>();
        for (SemanticMutant mutant : mutants.stream().limit(2).toList()) {
            if (!mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath()))) {
                surviving.add(mutant);
                continue;
            }
            BuildSummary result = runner.run(mutant, null);
            // A compile-only or unrelated-test failure is not evidence that the repair adopted the environment-validated counterexample.
            if (ContractWitnessProbe.discriminating(List.of(mutant.counterexample()), result.testNames(), result.testFailedNames()).isEmpty()) {
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
}
