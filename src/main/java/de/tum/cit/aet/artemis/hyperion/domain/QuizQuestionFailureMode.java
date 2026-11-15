package de.tum.cit.aet.artemis.hyperion.domain;

/**
 * Defects the quiz question filter judges a generated question on, each against the material the question was generated from.
 */
public enum QuizQuestionFailureMode {

    /** The question, the option marked correct, or the explanation is wrong or unanswerable. */
    FACTUAL_ERROR,

    /** More than one option could defensibly be correct, or correctness depends on an unstated assumption. */
    AMBIGUOUS_CORRECT_ANSWER,

    /** The question tests something outside the supplied material. */
    OFF_TOPIC,

    /** The question is answerable by verbatim recall of the material rather than by reasoning. */
    NEAR_DUPLICATE,

    /** The distractors give the answer away by form, plausibility or overlap. */
    ILL_FORMED_DISTRACTORS
}
