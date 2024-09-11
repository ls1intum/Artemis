package de.tum.cit.aet.artemis.domain.enumeration;

/**
 * Describes the status of a {@link de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupSession}
 */
public enum TutorialGroupSessionStatus {
    /**
     * The session is planned to occur
     */
    ACTIVE,
    /**
     * The session was originally planned to occur, but was cancelled
     */
    CANCELLED
}
