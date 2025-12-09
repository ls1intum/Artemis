package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

public record QuizExerciseDatesDTO(ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate) {

    public static QuizExerciseDatesDTO of(QuizExercise quizExercise) {
        return new QuizExerciseDatesDTO(quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate());
    }
}
