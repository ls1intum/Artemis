package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.DifferentialVerifier;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageChecks;

class StageGateEvaluatorTest {

    @Test
    void aMechanicallyValidTestsStageReachesStatementAuthoringBeforeOptionalAssessment() {
        var verifier = mock(DifferentialVerifier.class);
        var sandbox = new FakeInteractiveSandbox();
        var exercise = mock(GenerationInput.class);
        var tools = mock(SandboxAgentTools.class);
        when(tools.seededStructuralTests()).thenReturn(SeededStructuralTests.EMPTY);
        var checks = spy(new StageChecks(verifier, new ApprovedSpecRegistry()));
        var report = report(true, true, List.of(), List.of("testStudent"), List.of());
        var mechanicallyPassed = new StageCheckResult(true, "mechanical checks passed", report);
        doReturn(Optional.empty()).when(checks).correctGivenOwnership(sandbox, "s");
        doReturn(mechanicallyPassed).when(checks).check(GenerationStage.TESTS, sandbox, "s", exercise, Map.of(), null, SeededStructuralTests.EMPTY);
        when(verifier.probeOutputOnlyMains(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new AssertionError("Optional assessment started before the complete draft could be checkpointed"));

        var result = StageGateEvaluator.evaluate(checks, GenerationStage.TESTS, tools, sandbox, "s", exercise, Map.of(), null);

        assertThat(result.result().passed()).isTrue();
        assertThat(result.result().report()).isSameAs(report);
        assertThat(result.reused()).isFalse();
    }

    @Test
    void passingEveryStudentTestOnTheStarterReopensAssessmentWithoutCountingSuppliedChecks() {
        var report = report(true, true, List.of("testStudentOperation"), List.of("testStudentOperation", "testGivenBehavior", "testClass"), List.of("testGivenBehavior"));
        var seeded = new SeededStructuralTests(Set.of("testClass"), Map.of("test/ClassTest.java", "trusted structural test"));
        var result = StageGateEvaluator.classifyAssessment(new StageCheckResult(false, "template passes", report), seeded);

        assertThat(result.contractRedesignRequired()).isTrue();
        assertThat(result.report()).isSameAs(report);
    }

    @Test
    void aPartiallyDiscriminatingSuiteKeepsItsArtifactRepair() {
        var report = report(true, true, List.of("testMissingPartition"), List.of("testMissingPartition", "testDiscriminatingPartition"), List.of());
        var failure = new StageCheckResult(false, "one partition passes", report);
        assertThat(StageGateEvaluator.classifyAssessment(failure, SeededStructuralTests.EMPTY)).isSameAs(failure);
    }

    @Test
    void preservationOnlySuiteDoesNotAuthorizeContractRedesign() {
        var report = report(true, true, List.of(), List.of("testGivenBehavior"), List.of("testGivenBehavior"));
        var failure = new StageCheckResult(false, "no student-work test", report);
        assertThat(StageGateEvaluator.classifyAssessment(failure, SeededStructuralTests.EMPTY)).isSameAs(failure);
    }

    @Test
    void failedReferenceOrUncompiledStarterProvidesNoAssessmentProof() {
        for (var report : List.of(report(false, true, List.of("testStudent"), List.of("testStudent"), List.of()),
                report(true, false, List.of("testStudent"), List.of("testStudent"), List.of()))) {
            var failure = new StageCheckResult(false, "build failed", report);
            assertThat(StageGateEvaluator.classifyAssessment(failure, SeededStructuralTests.EMPTY)).isSameAs(failure);
        }
        var unavailable = StageCheckResult.failed("sandbox unavailable");
        assertThat(StageGateEvaluator.classifyAssessment(unavailable, SeededStructuralTests.EMPTY)).isSameAs(unavailable);
    }

    private AgentVerifyReport report(boolean solutionPassed, boolean templateCompiled, List<String> passing, List<String> names, List<String> preservation) {
        return new AgentVerifyReport(names.size(), solutionPassed, List.of(), List.of(), names.size(), templateCompiled, false, List.of(), passing, names, List.of(), List.of(),
                false, List.of(), List.of(), "", "", preservation);
    }
}
