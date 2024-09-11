package de.tum.cit.aet.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

public enum ExerciseLifecycle implements IExerciseLifecycle {
    RELEASE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getReleaseDate();
        }

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            return getDateFromExercise(quizExercise);
        }
    },
    START {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getStartDate();
        }

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            return quizBatch.getStartTime();
        }
    },
    DUE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getDueDate();
        }

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            return quizExercise.getDueDate();
        }
    },
    ASSESSMENT_DUE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getAssessmentDueDate();
        }

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            throw new IllegalStateException("Unexpected QuizExercise Lifecycle State " + this + " for exercise: " + quizExercise);
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

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            throw new IllegalStateException("Unexpected QuizExercise Lifecycle State " + this + " for exercise: " + quizExercise);
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

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            throw new IllegalStateException("Unexpected QuizExercise Lifecycle State " + this + " for exercise: " + quizExercise);
        }
    }
}
