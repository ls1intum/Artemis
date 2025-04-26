package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

/**
 * Class to encapsulate hidden page information for a single slide.
 */
public record HiddenPageInfo(ZonedDateTime hiddenDate, Long exerciseId) {

    /**
     * Checks if this hidden page info has an associated exercise.
     *
     * @return true if there is an associated exercise, false otherwise
     */
    public boolean hasExercise() {
        return exerciseId != null;
    }
}
