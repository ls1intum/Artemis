package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;

class SemanticMutantWorkspaceProbeTest {

    private final InteractiveSandbox sandbox = mock();

    @Test
    void restoresTheCandidateAfterASuccessfulProbe() {
        AtomicInteger restores = new AtomicInteger();
        BuildSummary summary = new BuildSummary(1, 0, 0, false, List.of("t"), List.of(), List.of(), "");

        assertThat(SemanticMutantWorkspaceProbe.run(sandbox, "s", null, null, restores::incrementAndGet, () -> {
        }, () -> summary)).isSameAs(summary);
        assertThat(restores).hasValue(2);
    }

    @Test
    void aFailedRestoreAfterAFailedBuildStopsVerificationAndKeepsTheBuildFailure() {
        AtomicInteger restores = new AtomicInteger();
        IllegalStateException buildFailure = new IllegalStateException("build failed");

        assertThatThrownBy(() -> SemanticMutantWorkspaceProbe.run(sandbox, "s", null, null, () -> {
            if (restores.incrementAndGet() > 1) {
                throw new IllegalStateException("restore failed");
            }
        }, () -> {
        }, () -> {
            throw buildFailure;
        })).isInstanceOf(DifferentialVerifier.VerificationInfrastructureException.class).hasRootCauseMessage("restore failed").hasSuppressedException(buildFailure);
    }

    @Test
    void aFailedBuildIsRethrownWhenTheCandidateWasRestored() {
        IllegalStateException buildFailure = new IllegalStateException("build failed");

        assertThatThrownBy(() -> SemanticMutantWorkspaceProbe.run(sandbox, "s", null, null, () -> {
        }, () -> {
        }, () -> {
            throw buildFailure;
        })).isSameAs(buildFailure);
    }

    @Test
    void aFailedRestoreAfterASuccessfulBuildStopsVerification() {
        AtomicInteger restores = new AtomicInteger();

        assertThatThrownBy(() -> SemanticMutantWorkspaceProbe.run(sandbox, "s", null, null, () -> {
            if (restores.incrementAndGet() > 1) {
                throw new IllegalStateException("restore failed");
            }
        }, () -> {
        }, () -> new BuildSummary(1, 0, 0, false, List.of("t"), List.of(), List.of(), ""))).isInstanceOf(DifferentialVerifier.VerificationInfrastructureException.class)
                .hasRootCauseMessage("restore failed");
    }
}
