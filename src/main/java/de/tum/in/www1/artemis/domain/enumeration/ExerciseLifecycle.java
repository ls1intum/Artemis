package de.tum.in.www1.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

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
    },
    BUILD_AND_TEST_AFTER_DUE_DATE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            if (!(exercise instanceof ProgrammingExercise)) {
                throw new IllegalStateException("Unexpected Exercise Lifecycle State " + this.toString() + "for exercise: " + exercise);
            }
            return ((ProgrammingExercise) exercise).getBuildAndTestStudentSubmissionsAfterDueDate();
        }
    }
}
