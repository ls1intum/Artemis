package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic.SpecificationReview;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult;

class ExecutableSpecificationReviewTest {

    private final SpecFidelityCritic reviewer = mock(SpecFidelityCritic.class);

    private final ExecutableSpecificationReview review = new ExecutableSpecificationReview(reviewer, "Preserve both learner goals", new AgentTranscriptWriter(""), 1L, null,
            () -> false, null);

    private static AgentVerifyReport report(boolean passed, int tests) {
        return new AgentVerifyReport(tests, passed, passed ? List.of() : List.of("checksRule"),
                passed ? List.of() : List.of(new AgentVerifyReport.TestFailureEvidence("checksRule", "expected 4 but was 3")), tests, true, true, List.of(), List.of(),
                List.of("checksRule"), List.of(), List.of(), passed, passed ? List.of() : List.of("reference assertion failed"));
    }

    private static SpecificationReview conflict() {
        return new SpecificationReview(true, false, true, List.of("The worked example contradicts the computation rule"), "Two conflicting contract passages", null);
    }

    @Test
    void anAuthoringStopPreventsAnotherOptionalProviderCall() {
        var stopped = new ExecutableSpecificationReview(reviewer, "brief", new AgentTranscriptWriter(""), 1L, null, () -> true, null);
        var passed = new StageCheckResult(true, "Verified", report(true, 1));

        var result = stopped.check(GenerationStage.TESTS, "Frozen specification", null, null, passed, null, false);

        assertThat(result.gate()).isSameAs(passed);
        verifyNoInteractions(reviewer);
    }

    @Test
    void anEarlierReferenceFailureCannotBeErasedByMakingAnIncorrectGraderPass() {
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(conflict());
        var passed = new StageCheckResult(true, "Passed current grader", report(true, 1));

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, passed, report(false, 1), true);

        assertThat(result.gate().outcome()).isEqualTo(StageCheckResult.Outcome.SPECIFICATION_CONFLICT);
        assertThat(result.unresolvedFindings()).isEmpty();
        var context = ArgumentCaptor.forClass(String.class);
        verify(reviewer).reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), context.capture(), any(), any());
        assertThat(context.getValue()).contains("expected 4 but was 3", "reference passed=true");
    }

    @Test
    void aValidFirstDraftKeepsItsContractFindingsWhenTheCorrectionBudgetIsSpent() {
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(conflict());
        var passed = new StageCheckResult(true, "Verified", report(true, 1));

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, passed, null, false);

        assertThat(result.gate()).isSameAs(passed);
        assertThat(result.gate().passed()).isTrue();
        assertThat(result.unresolvedFindings()).containsExactlyElementsOf(conflict().findings());
    }

    @Test
    void aFailedMechanicalArtifactNeverBecomesAPassWhenContractRepairIsUnavailable() {
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(conflict());
        AgentVerifyReport failedReport = report(false, 1);
        var failed = new StageCheckResult(false, "Reference fails", failedReport);

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, failed, failedReport, false);

        assertThat(result.gate()).isSameAs(failed);
        assertThat(result.gate().passed()).isFalse();
        assertThat(result.unresolvedFindings()).isNotEmpty();
    }

    @Test
    void aCompilerFailureAloneDoesNotRequestContractReview() {
        var failed = new StageCheckResult(false, "Compilation failed", report(false, 0));

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, failed, null, true);

        assertThat(result.gate()).isSameAs(failed);
        assertThat(result.unresolvedFindings()).isEmpty();
        verifyNoInteractions(reviewer);
    }

    @Test
    void reviewIsBoundedToOneCallPerUnchangedSpecification() {
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(new SpecificationReview(true, List.of()));
        var passed = new StageCheckResult(true, "Verified", report(true, 1));

        assertThat(review.check(GenerationStage.TESTS, "Same specification", null, null, passed, null, true).gate()).isSameAs(passed);
        assertThat(review.check(GenerationStage.TESTS, "Same specification", null, null, passed, null, true).gate()).isSameAs(passed);
        assertThat(review.check(GenerationStage.TESTS, "Revised specification", null, null, passed, null, true).gate()).isSameAs(passed);

        verify(reviewer, times(2)).reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void unavailableReviewPreservesAVerifiedDraftAndDisclosesTheMissingEvidence() {
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenThrow(new IllegalStateException("Reviewer offline"));
        var passed = new StageCheckResult(true, "Verified", report(true, 1));

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, passed, null, true);

        assertThat(result.gate()).isSameAs(passed);
        assertThat(result.unresolvedFindings()).singleElement().asString().contains("did not complete");
    }

    @Test
    void anIncompleteVerdictKeepsPreviouslyGroundedConcernsForAdjudication() {
        var incomplete = new SpecificationReview(false, false, false, List.of(), "Concept citation could not be validated", null, List.of("The example conflicts with the rule"));
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(incomplete);
        var passed = new StageCheckResult(true, "Verified", report(true, 1));

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, passed, null, true);

        assertThat(result.gate()).isSameAs(passed);
        assertThat(result.review()).isNull();
        assertThat(result.unresolvedFindings()).anySatisfy(finding -> assertThat(finding).contains("requiring adjudication", "example conflicts with the rule"));
    }

    @Test
    void anIncompleteVerdictCannotCertifyContractConsistency() {
        when(reviewer.reviewSpecificationWithExecution(anyString(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(new SpecificationReview(false, List.of()));
        var passed = new StageCheckResult(true, "Verified", report(true, 1));

        var result = review.check(GenerationStage.TESTS, "Frozen specification", null, null, passed, null, true);

        assertThat(result.gate()).isSameAs(passed);
        assertThat(result.review()).isNull();
        assertThat(result.unresolvedFindings()).singleElement().asString().contains("incomplete");
    }
}
