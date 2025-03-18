package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizParticipationWithSolutionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, QuizExerciseWithSolutionDTO exercise) {

    public static StudentQuizParticipationWithSolutionsDTO of(final StudentParticipation studentParticipation) {
        Exercise participationExercise = studentParticipation.getExercise();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // TODO: Figure out error handling here
            return null;
        }
        return new StudentQuizParticipationWithSolutionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithSolutionDTO.of(quizExercise));
    }
}
