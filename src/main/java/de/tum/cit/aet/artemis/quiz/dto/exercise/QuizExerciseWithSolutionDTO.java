package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseWithSolutionDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExerciseWithoutQuestionsDTO, List<QuizQuestionWithSolutionDTO> quizQuestions) {

    public static QuizExerciseWithSolutionDTO of(QuizExercise quizExercise) {
        return new QuizExerciseWithSolutionDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise),
                quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList());
    }

}
