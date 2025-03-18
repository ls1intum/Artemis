package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

public record StudentQuizParticipationWithoutQuestionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO,
        QuizExerciseWithoutQuestionsDTO exercise) {

    public static StudentQuizParticipationWithoutQuestionsDTO of(final StudentParticipation studentParticipation) {
        Exercise participationExercise = studentParticipation.getExercise();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // TODO: Figure out error handling here
            return null;
        }
        return new StudentQuizParticipationWithoutQuestionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithoutQuestionsDTO.of(quizExercise));
    }
}
