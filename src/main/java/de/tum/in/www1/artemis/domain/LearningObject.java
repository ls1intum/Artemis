package de.tum.in.www1.artemis.domain;

import java.util.Set;

import de.tum.in.www1.artemis.domain.competency.Competency;

public interface LearningObject {

    /**
     * Whether the participant has completed the object
     *
     * @param user the user to check
     * @return True if completed, else false
     */
    boolean isCompletedFor(User user);

    Long getId();

    Set<Competency> getCompetencies();
}
