package de.tum.in.www1.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;

public enum ExerciseLifecycle implements IExerciseLifecycle {
    RELEASE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getReleaseDate();
        }
    },
    START {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getStartDate();
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
                throw new IllegalStateException("Unexpected Exercise Lifecycle State " + this + " for exercise: " + exercise);
            }
            return ((ProgrammingExercise) exercise).getBuildAndTestStudentSubmissionsAfterDueDate();
        }
    },
    BUILD_COMPASS_CLUSTERS_AFTER_EXAM {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            if (!(exercise instanceof ModelingExercise)) {
                throw new IllegalStateException("Unexpected Exercise Lifecycle State " + this + "for exercise: " + exercise);
            }
            return ((ModelingExercise) exercise).getClusterBuildDate();
        }
    }
}
