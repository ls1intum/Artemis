package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

public enum ExerciseLifecycle implements IExerciseLifecycle {
    SHORTLY_BEFORE_RELEASE {

        @Override
        public ZonedDateTime getDateFromExercise(Exercise exercise) {
            return exercise.getReleaseDate() != null ? exercise.getReleaseDate().minusSeconds(15) : null;
        }

        @Override
        public ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise) {
            return getDateFromExercise(quizExercise);
        }
    },

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
    }
}
