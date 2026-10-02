package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class VerificationResultTest {

    @Test
    void rejectsContradictoryVerificationClaims() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationResult(true, false, true, 3, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationResult(true, true, false, 3, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationResult(true, true, true, 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationResult(true, true, true, 3, List.of("Harness was modified")));
    }

    @Test
    void bindsAVerifiedResultToTheExactCandidate() {
        WorkspaceSnapshot candidate = new WorkspaceSnapshot(List.of(new WorkspaceFile("solution/A.java", new byte[] { 1 }, false)));
        var verification = new VerificationResult(true, true, true, 3, List.of());
        var review = new SpecFidelityReport(List.of());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationOutput(candidate, verification, "wrong", review, "CONVERGED", null, GenerationOutput.AccountingState.INCOMPLETE, "standard"));
        var output = new GenerationOutput(candidate, verification, candidate.sha256(), review, "CONVERGED", null, GenerationOutput.AccountingState.INCOMPLETE, "standard");
        var identity = new ExecutionIdentity("job", 1, java.util.UUID.randomUUID(), "worker-1", java.util.UUID.randomUUID());
        var event = new WorkerEvent(WorkerCommand.PROTOCOL_VERSION, identity.workerId(), identity.workerIncarnation(), 1, java.time.Instant.now(), WorkerEvent.Type.FINISHED,
                identity, false, "sha256:" + "a".repeat(64), null, null, output);
        WorkerMessageCodec codec = new WorkerMessageCodec();
        assertThat(codec.decodeEvent(codec.encode(event)).output()).isEqualTo(output);
        assertThat(output.verification().report()).contains("all 3 tests");
    }
}
