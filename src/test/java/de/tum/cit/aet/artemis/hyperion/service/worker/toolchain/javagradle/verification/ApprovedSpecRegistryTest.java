package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.BriefLearningGoals;

class ApprovedSpecRegistryTest {

    @Test
    void sourceGoalsSurviveApprovalAmendmentAndAssessmentReentryUntilTheSessionIsForgotten() {
        var registry = new ApprovedSpecRegistry();
        var goals = new BriefLearningGoals("Preserve order and choose the smallest holder.",
                List.of(new BriefLearningGoals.Goal("G1", "Preserve order"), new BriefLearningGoals.Goal("G2", "choose the smallest holder")));
        registry.rememberLearningGoals("s", goals);
        registry.approve("s", "first specification");
        assertThat(registry.learningGoals("s")).contains(goals);

        assertThat(registry.amend("s", "first specification", "ownership correction")).isTrue();
        assertThat(registry.learningGoals("s")).contains(goals);
        assertThat(registry.reopenForAssessment("s", "ownership correction")).isTrue();
        assertThat(registry.approved("s")).isEmpty();
        assertThat(registry.learningGoals("s")).contains(goals);

        var reducedScope = new BriefLearningGoals(goals.sourceBrief(), List.of(goals.goals().getFirst()));
        assertThatThrownBy(() -> registry.rememberLearningGoals("s", reducedScope)).isInstanceOf(IllegalStateException.class);
        registry.approve("s", "redesigned specification");
        assertThat(registry.learningGoals("s")).contains(goals);

        registry.forget("s");
        assertThat(registry.learningGoals("s")).isEmpty();
        assertThat(registry.approved("s")).isEmpty();
    }

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
