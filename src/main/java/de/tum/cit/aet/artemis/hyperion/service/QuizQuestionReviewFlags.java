package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;

import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;

/**
 * Decides which filter verdicts ask a reviewer to look at a question the filter accepted.
 * <p>
 * Factual error, ambiguous answer and off topic flag from any severity above zero, weak distractors from
 * {@link #WEAK_DISTRACTOR_FLAG_SEVERITY} on, and a recall-only verdict never flags. Every verdict stays part of the decision
 * regardless.
 */
public final class QuizQuestionReviewFlags {

    /** The severity from which weak distractors flag a question. */
    public static final double WEAK_DISTRACTOR_FLAG_SEVERITY = 0.4;

    private QuizQuestionReviewFlags() {
    }

    /**
     * Tells whether one verdict flags the question for review.
     *
     * @param verdict the verdict
     * @return whether a reviewer should look at the question because of this verdict
     */
    public static boolean flagsForReview(QuizQuestionFilterVerdictDTO verdict) {
        return switch (verdict.mode()) {
            case NEAR_DUPLICATE -> false;
            case ILL_FORMED_DISTRACTORS -> verdict.severity() >= WEAK_DISTRACTOR_FLAG_SEVERITY;
            case FACTUAL_ERROR, AMBIGUOUS_CORRECT_ANSWER, OFF_TOPIC -> verdict.triggered() || verdict.severity() > 0;
        };
    }

    /**
     * Tells whether a decision flags the question for review.
     *
     * @param decision the filter's decision
     * @return whether at least one verdict flags the question
     */
    public static boolean isFlagged(QuizQuestionFilterDecisionDTO decision) {
        return decision.verdicts().stream().anyMatch(QuizQuestionReviewFlags::flagsForReview);
    }

    /**
     * Returns the decision with every verdict's flag derived from its mode and severity.
     *
     * @param decision the filter's decision
     * @return the same decision with {@link QuizQuestionFilterVerdictDTO#flagged()} set on each verdict
     */
    public static QuizQuestionFilterDecisionDTO withFlags(QuizQuestionFilterDecisionDTO decision) {
        List<QuizQuestionFilterVerdictDTO> verdicts = decision.verdicts().stream()
                .map(verdict -> new QuizQuestionFilterVerdictDTO(verdict.mode(), verdict.severity(), verdict.triggered(), verdict.justification(), flagsForReview(verdict)))
                .toList();
        return new QuizQuestionFilterDecisionDTO(decision.accepted(), decision.aggregateScore(), verdicts, decision.rationale());
    }

}
