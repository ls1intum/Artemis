package de.tum.in.www1.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Exercise;

public enum ExerciseLifecycle implements IExerciseLifecycle {
    RELEASE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getReleaseDate();
        }
    },
    DUE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getDueDate();
        }
    },
    ASSESSMENT_DUE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getAssessmentDueDate();
        }
    };
}
