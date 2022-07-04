package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The QuitMode enumeration.
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
     * students can participate in the quit at any time between the release and due date
     */
    INDIVIDUAL
}
