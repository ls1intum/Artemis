package de.tum.in.www1.artemis.domain.enumeration;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

public interface IExerciseLifecycle {

    ZonedDateTime getDateFromExercise(Exercise exercise);

    ZonedDateTime getDateFromQuizBatch(QuizBatch quizBatch, QuizExercise quizExercise);
}
