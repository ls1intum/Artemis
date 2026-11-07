package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthoringProgressMonitorTest {

    @Test
    void measuringAnUnchangedWorkspaceCannotExtendAuthoring() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(1, 0, Integer.MAX_VALUE, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 0, Integer.MAX_VALUE, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 1, 4, false))).isEqualTo(AuthoringProgressMonitor.Decision.STALLED);
    }

    @Test
    void aChangedDiagnosticWithoutAnEditIsNotARepair() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(1, 1, 8, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 2, 4, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 3, 2, false))).isEqualTo(AuthoringProgressMonitor.Decision.STALLED);
    }

    @Test
    void editsAwaitingVerificationCanEarnCreditInTheNextBlock() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(0, 1, 8, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 1, 8, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 2, 4, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
    }

    @Test
    void aPreviousStagesPassCannotHideCurrentVerificationProgress() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(10, 3, 0, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(20, 4, 8, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(30, 5, 3, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(40, 5, 3, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(50, 6, 8, false))).isEqualTo(AuthoringProgressMonitor.Decision.STALLED);
    }

    @Test
    void writesAloneCannotExtendAStalledRepair() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(0, 1, 4, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(20, 1, 4, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(40, 2, 4, false))).isEqualTo(AuthoringProgressMonitor.Decision.STALLED);
    }

    @Test
    void aVerifiedReductionResetsStagnationButARegressionDoesNot() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(0, 1, 4, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(1, 1, 4, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(2, 2, 2, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(3, 3, 3, false))).isEqualTo(AuthoringProgressMonitor.Decision.CONTINUE);
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(4, 4, 2, false))).isEqualTo(AuthoringProgressMonitor.Decision.STALLED);
    }

    @Test
    void anUnchangedPassingWorkspaceReturnsToTheAuthoritativeGate() {
        var monitor = new AuthoringProgressMonitor(new AuthoringProgress.Snapshot(0, 0, Integer.MAX_VALUE, false));
        assertThat(monitor.assess(new AuthoringProgress.Snapshot(3, 1, 0, true))).isEqualTo(AuthoringProgressMonitor.Decision.VERIFIED);
    }
}
