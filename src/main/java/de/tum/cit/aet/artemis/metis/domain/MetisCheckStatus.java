package de.tum.cit.aet.artemis.metis.domain;

/**
 * Lifecycle state of a {@link MetisCheck}. A check starts as {@link #PENDING}, moves through generation and the
 * optional instructor review to {@link #READY}, is {@link #IN_PROGRESS} once the student has seen the first question
 * and ends in {@link #COMPLETED}. It leaves that path as {@link #FAILED} or {@link #SKIPPED}, in which case the check
 * carries a failure reason.
 */
public enum MetisCheckStatus {

    /** Created for the participation; generation has not started yet. */
    PENDING,

    /** The LLM is generating the questions from the student's git diff. */
    GENERATING,

    /** Questions exist and wait for instructor approval because the exercise requires a review. */
    IN_REVIEW,

    /** Questions are available to the student, who has not seen any of them yet. */
    READY,

    /** The student has been shown the first question. */
    IN_PROGRESS,

    /** Every question has been answered and rated. */
    COMPLETED,

    /** Generation or rating failed; the reason is on the check. */
    FAILED,

    /** No check was possible for this participation, for example without a submission or with an empty diff; the reason is on the check. */
    SKIPPED
}
