package de.tum.cit.aet.artemis.hyperion.domain;

/**
 * The instructor's decision on a pool question, taken after the filter has judged it.
 */
public enum QuizQuestionReviewState {

    /** Not reviewed yet. */
    PENDING,

    /** Approved for use in quizzes. */
    APPROVED,

    /** Excluded from use in quizzes. */
    EXCLUDED
}
