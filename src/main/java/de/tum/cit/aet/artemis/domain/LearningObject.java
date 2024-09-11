package de.tum.cit.aet.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

public interface LearningObject {

    /**
     * Get the date when the object has been completed by the participant
     *
     * @param user the user to retrieve the date for
     * @return The datetime when the object was first completed or null
     */
    Optional<ZonedDateTime> getCompletionDate(User user);

    Long getId();

    Set<CourseCompetency> getCompetencies();

    boolean isVisibleToStudents();
}
