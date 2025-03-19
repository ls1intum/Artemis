package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseWithQuestionsDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExerciseWithoutQuestionsDTO, List<QuizQuestionWithoutSolutionDTO> quizQuestions) {

    public static QuizExerciseWithQuestionsDTO of(QuizExercise quizExercise) {
        return new QuizExerciseWithQuestionsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise),
                quizExercise.getQuizQuestions().stream().map(QuizQuestionWithoutSolutionDTO::of).toList());
    }

}
