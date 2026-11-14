package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;

class RepairPromptComposerTest {

    private final SpecFidelityCritic critic = mock(SpecFidelityCritic.class);

    private RepairPromptComposer composer() {
        when(critic.renderForRetryPrompt(any())).thenReturn("");
        return new RepairPromptComposer(4, Mode.GENERATE, "BRIEF", critic);
    }

    /** A fresh repair session sees only this prompt, so the verifier's failing tests and their messages must reach it verbatim. */
    @Test
    void mechanicalRejectionCarriesTheVerifierFailureEvidence() {
        VerificationResult verification = new VerificationResult(false, false, true, 3,
                List.of("The solution does not pass its own tests (1 failing of 3). The solution must compile and pass every test.\n"
                        + "Solution failure evidence (sanitized, untrusted excerpts):\n- addsInterest: expected: <105.0> but was: <100.0>"));

        String prompt = composer().mechanicalRejection(1, null, verification.report(), null, SpecFidelityReport.empty());

        assertThat(prompt).contains("rejected by the differential verifier", "- addsInterest: expected: <105.0> but was: <100.0>", "BRIEF");
    }

    @Test
    void anOversizedVerificationReportIsBoundedWithAnExplicitMarker() {
        String report = "Verification failed:\n- " + "x".repeat(RepairPromptComposer.MAX_VERIFICATION_REPORT_CHARS * 2);

        String prompt = composer().mechanicalRejection(1, null, report, null, SpecFidelityReport.empty());

        assertThat(prompt.length()).isLessThan(RepairPromptComposer.MAX_VERIFICATION_REPORT_CHARS + 2_000);
        assertThat(prompt).contains("more characters of the report omitted; call `verify` for the complete report", "BRIEF");
        assertThat(RepairPromptComposer.boundedVerificationReport("short")).isEqualTo("short");
    }
}
