package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

public interface IExerciseLifecycle {

    ZonedDateTime getDateFromExercise(Exercise exercise);

    ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise);
}
