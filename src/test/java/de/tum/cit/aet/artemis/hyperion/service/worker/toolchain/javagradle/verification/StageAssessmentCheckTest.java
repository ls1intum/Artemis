package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

class StageAssessmentCheckTest {

    private static final String SOURCE = "class Client { public static void main(String[] args) {} }";

    private final FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox() {

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            if (path.equals("/workspace/solution")) {
                return new TarArchiveInputStream(WorkspaceArchive.buildWorkspaceTarStream(Map.of("solution/src/Client.java", SOURCE), Map.of()));
            }
            return super.copyOut(sessionId, path);
        }
    };

    private final DifferentialVerifier verifier = mock(DifferentialVerifier.class);

    private final AgentVerifyReport report = mock(AgentVerifyReport.class);

    private final GenerationInput exercise = mock(GenerationInput.class);

    @Test
    void aSurvivingTranscriptRequestsContractRedesignAndRestoresTheOriginalSources() {
        var mutant = SemanticMutant.printedTranscript("src/Client.java", SOURCE, "print only", "Client");
        when(verifier.probeOutputOnlyMains(any(), anyString(), any(), anyMap(), anyMap(), anyMap(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            sandbox.withFile("/workspace/solution/src/Client.java", "print only");
            return List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE));
        });

        StageCheckResult result = check().orElseThrow();

        assertThat(result.passed()).isFalse();
        assertThat(result.contractRedesignRequired()).isTrue();
        assertThat(result.observation()).contains("src/Client.java", "only prints");
        assertThat(result.report()).isSameAs(report);
        assertThat(sandbox.files()).containsEntry("/workspace/solution/src/Client.java", SOURCE);
    }

    @Test
    void anInconclusiveProbeDoesNotAuthorizeChangingTheContract() {
        var mutant = SemanticMutant.printedTranscript("src/Client.java", SOURCE, "print only", "Client");
        when(verifier.probeOutputOnlyMains(any(), anyString(), any(), anyMap(), anyMap(), anyMap(), anyString(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE)));
        StageCheckResult result = check().orElseThrow();
        assertThat(result.passed()).isFalse();
        assertThat(result.contractRedesignRequired()).isFalse();
    }

    @Test
    void infrastructureFailurePropagatesAndRestoresTheCandidate() {
        when(verifier.probeOutputOnlyMains(any(), anyString(), any(), anyMap(), anyMap(), anyMap(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            sandbox.withFile("/workspace/solution/src/Client.java", "print only");
            throw new IllegalStateException("session lost");
        });
        assertThatThrownBy(this::check).isInstanceOf(IllegalStateException.class).hasMessage("session lost");
        assertThat(sandbox.files()).containsEntry("/workspace/solution/src/Client.java", SOURCE);
    }

    @Test
    void staleApprovalCannotReopenAnotherContract() {
        var registry = new ApprovedSpecRegistry();
        registry.approve("s", "first");
        assertThat(registry.reopenForAssessment("s", "stale")).isFalse();
        assertThat(registry.approved("s")).hasValue("first");
        assertThat(registry.reopenForAssessment("s", "first")).isTrue();
        registry.approve("s", "redesigned");
        assertThat(registry.reopenForAssessment("s", "first")).isFalse();
        assertThat(registry.approved("s")).hasValue("redesigned");
    }

    @Test
    void reapprovalAndOwnershipCorrectionCannotRefreshTheSessionRedesignAllowance() {
        var registry = new ApprovedSpecRegistry();
        registry.approve("s", "first");
        assertThat(registry.reopenForAssessment("s", "first")).isTrue();
        registry.approve("s", "redesigned");
        assertThat(registry.amend("s", "redesigned", "ownership correction")).isTrue();

        assertThat(registry.assessmentReopened("s")).isTrue();
        assertThat(registry.reopenForAssessment("s", "ownership correction")).isFalse();
        assertThat(registry.approved("s")).hasValue("ownership correction");
        registry.forget("s");
        registry.approve("s", "new session");
        assertThat(registry.reopenForAssessment("s", "new session")).isTrue();
    }

    private java.util.Optional<StageCheckResult> check() {
        return StageAssessmentCheck.failure(verifier, sandbox, "s", exercise, SeededStructuralTests.EMPTY, report);
    }
}
