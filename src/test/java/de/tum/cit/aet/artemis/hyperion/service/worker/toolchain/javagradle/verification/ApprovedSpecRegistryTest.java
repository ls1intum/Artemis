package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApprovedSpecRegistryTest {

    @Test
    void approvalIsImmutableButIdempotent() {
        ApprovedSpecRegistry registry = new ApprovedSpecRegistry();

        registry.approve("session", "approved contract");
        registry.approve("session", "approved contract");

        assertThat(registry.approved("session")).contains("approved contract");
        assertThatThrownBy(() -> registry.approve("session", "weaker contract")).isInstanceOf(IllegalStateException.class).hasMessageContaining("different specification");
        assertThat(registry.approved("session")).contains("approved contract");
    }

    @Test
    void anAmendmentReplacesOnlyTheExactContractItWasDerivedFrom() {
        ApprovedSpecRegistry registry = new ApprovedSpecRegistry();

        assertThat(registry.amend("session", "approved contract", "corrected contract")).isFalse();
        registry.approve("session", "approved contract");

        assertThat(registry.amend("session", "stale contract", "corrected contract")).isFalse();
        assertThat(registry.approved("session")).contains("approved contract");
        assertThat(registry.amend("session", "approved contract", "corrected contract")).isTrue();
        assertThat(registry.approved("session")).contains("corrected contract");
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

}
