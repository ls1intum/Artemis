package de.tum.cit.aet.artemis.domain.enumeration;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * Describes the status of a {@link TutorialGroupSession}
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
