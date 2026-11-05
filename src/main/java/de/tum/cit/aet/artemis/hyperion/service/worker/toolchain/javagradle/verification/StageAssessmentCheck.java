package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;

/** Executes assessment counterexamples at the artifact boundary, before the contract reaches statement authoring. */
final class StageAssessmentCheck {

    private StageAssessmentCheck() {
    }

    static Optional<StageCheckResult> failure(DifferentialVerifier verifier, InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, SeededStructuralTests seeded,
            AgentVerifyReport report) {
        JavaSourceSnapshot snapshot = JavaSourceSnapshot.capture(sandbox, sessionId);
        String plan = Objects.requireNonNullElse(GenerationWorkspace.readRootArtifact(sandbox, sessionId, "test-plan.json"), "");
        List<SemanticMutantOutcome> outcomes = new ArrayList<>();
        try {
            outcomes.addAll(verifier.probeOutputOnlyMains(sandbox, sessionId, exercise, snapshot.testsFiles(), snapshot.templateFiles(), snapshot.solutionFiles(), plan, seeded,
                    Set.of(), () -> snapshot.restore(sandbox, sessionId)));
            outcomes.addAll(verifier.probeStudentTestAssertions(sandbox, sessionId, exercise, snapshot.templateFiles(), snapshot.solutionFiles(), plan, seeded,
                    () -> snapshot.restore(sandbox, sessionId)));
        }
        finally {
            snapshot.restore(sandbox, sessionId);
        }
        List<String> ineffectiveTests = outcomes.stream()
                .filter(outcome -> outcome.mutant().testsLearnerAssertions() && outcome.disposition() == SemanticMutantOutcome.Disposition.SURVIVED_GRADED_SUITE)
                .map(outcome -> outcome.mutant().solutionPath()).toList();
        if (!ineffectiveTests.isEmpty()) {
            return Optional.of(StageCheckResult.redesignRequired("The graded suite accepts empty learner-written JUnit test bodies or tests that suppress assertion failures in "
                    + ineffectiveTests
                    + ". Running student tests only against the correct supplied implementation does not assess their assertions. Preserve the brief and grade whether learner tests reject faulty implementations, using supplied runtime support rather than inspecting their source.",
                    report));
        }
        List<String> surviving = outcomes.stream().filter(outcome -> outcome.disposition() == SemanticMutantOutcome.Disposition.SURVIVED_GRADED_SUITE)
                .map(outcome -> outcome.mutant().solutionPath()).toList();
        if (!surviving.isEmpty()) {
            return Optional.of(StageCheckResult.redesignRequired("The graded tests accept a replacement that only prints the reference transcript in " + surviving
                    + ". The assessment does not establish the student-owned behavior. Reconsider the contract and supported runtime observations before authoring the statement.",
                    report));
        }
        if (outcomes.stream().anyMatch(outcome -> outcome.disposition() == SemanticMutantOutcome.Disposition.INCONCLUSIVE)) {
            return Optional.of(new StageCheckResult(false,
                    "The assessment counterexample produced no conclusive build/test verdict. Keep the contract unchanged and fix the reported executable artifacts.\n"
                            + outcomes.stream().filter(outcome -> outcome.disposition() == SemanticMutantOutcome.Disposition.INCONCLUSIVE).map(SemanticMutantOutcome::diagnostic)
                                    .filter(diagnostic -> !diagnostic.isBlank()).distinct().collect(java.util.stream.Collectors.joining("\n")),
                    report));
        }
        return Optional.empty();
    }
}
