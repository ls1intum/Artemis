package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionFailureMode;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;

class QuizQuestionReviewFlagsTest {

    private static QuizQuestionFilterVerdictDTO verdict(QuizQuestionFailureMode mode, double severity, boolean triggered) {
        return new QuizQuestionFilterVerdictDTO(mode, severity, triggered, "because");
    }

    @Test
    void flagsForReview_flagsGatingModesFromAnySeverity() {
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.FACTUAL_ERROR, 0.1, false))).isTrue();
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.AMBIGUOUS_CORRECT_ANSWER, 0.0, false))).isFalse();
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.OFF_TOPIC, 0.0, true))).isTrue();
    }

    @Test
    void flagsForReview_flagsWeakDistractorsOnlyFromTheThreshold() {
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS, 0.3, false))).isFalse();
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS, 0.4, false))).isTrue();
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS, 0.1, true))).isFalse();
    }

    @Test
    void flagsForReview_neverFlagsRecallOnly() {
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.NEAR_DUPLICATE, 0.4, false))).isFalse();
        assertThat(QuizQuestionReviewFlags.flagsForReview(verdict(QuizQuestionFailureMode.NEAR_DUPLICATE, 1.0, true))).isFalse();
    }

    @Test
    void withFlags_marksEachVerdictAndKeepsTheRest() {
        QuizQuestionFilterDecisionDTO decision = new QuizQuestionFilterDecisionDTO(true, 0.9,
                List.of(verdict(QuizQuestionFailureMode.NEAR_DUPLICATE, 0.9, true), verdict(QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS, 0.4, false)), "checked");

        QuizQuestionFilterDecisionDTO flagged = QuizQuestionReviewFlags.withFlags(decision);

        assertThat(flagged.accepted()).isTrue();
        assertThat(flagged.aggregateScore()).isEqualTo(0.9);
        assertThat(flagged.rationale()).isEqualTo("checked");
        assertThat(flagged.verdicts()).extracting(QuizQuestionFilterVerdictDTO::flagged).containsExactly(false, true);
        assertThat(flagged.verdicts()).extracting(QuizQuestionFilterVerdictDTO::justification).containsOnly("because");
        assertThat(QuizQuestionReviewFlags.isFlagged(decision)).isTrue();
    }
}
