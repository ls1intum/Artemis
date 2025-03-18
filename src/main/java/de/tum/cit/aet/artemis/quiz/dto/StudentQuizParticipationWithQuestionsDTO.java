package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizParticipationWithQuestionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, QuizExerciseWithQuestionsDTO exercise) {

    public static StudentQuizParticipationWithQuestionsDTO of(final StudentParticipation studentParticipation) {
        Exercise participationExercise = studentParticipation.getExercise();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // TODO: Figure out error handling here
            return null;
        }
        return new StudentQuizParticipationWithQuestionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithQuestionsDTO.of(quizExercise));
    }
}
