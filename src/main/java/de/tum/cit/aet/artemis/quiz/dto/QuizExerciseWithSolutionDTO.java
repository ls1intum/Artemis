package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

public record QuizExerciseWithSolutionDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExerciseWithoutQuestionsDTO, List<QuizQuestionWithSolutionDTO> quizQuestions) {

    public static QuizExerciseWithSolutionDTO of(QuizExercise quizExercise) {
        return new QuizExerciseWithSolutionDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise),
                quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList());
    }
}
