package de.tum.cit.aet.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.quiz.QuizBatch;
import de.tum.cit.aet.artemis.domain.quiz.QuizExercise;

public interface IExerciseLifecycle {

    ZonedDateTime getDateFromExercise(Exercise exercise);

    ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise);
}
