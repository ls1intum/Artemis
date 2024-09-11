package de.tum.cit.aet.artemis.domain.enumeration;

/**
 * The QuizMode enumeration. Describe the different (participation) modes of a quiz.
 */
public enum QuizMode {
    /**
     * there is only one opportunity to participate in the quiz for all students at the same time
     */
    SYNCHRONIZED,
    /**
     * there are multiple opportunities where students can participate in the quiz
     */
    BATCHED,
    /**
     * students can participate in the quiz at any time between the release and due date
     */
    INDIVIDUAL
}
