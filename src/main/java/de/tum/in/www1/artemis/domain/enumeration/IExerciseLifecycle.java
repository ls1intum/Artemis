package de.tum.in.www1.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Exercise;

public interface IExerciseLifecycle {

    ZonedDateTime getDateFromExercise(Exercise exercise);
}
