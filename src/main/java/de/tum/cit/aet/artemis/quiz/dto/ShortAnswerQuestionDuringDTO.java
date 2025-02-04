package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionDuringDTO(@JsonUnwrapped QuizQuestionDuringDTO quizQuestionDuringDTO, List<ShortAnswerSpotDTO> spots, List<ShortAnswerSolutionDTO> solutions,
        Integer similarityValue, Boolean matchLetterCase) {

    public static ShortAnswerQuestionDuringDTO of(ShortAnswerQuestion shortAnswerQuestion) {
        return new ShortAnswerQuestionDuringDTO(QuizQuestionDuringDTO.of(shortAnswerQuestion), shortAnswerQuestion.getSpots().stream().map(ShortAnswerSpotDTO::of).toList(),
                shortAnswerQuestion.getSolutions().stream().map(ShortAnswerSolutionDTO::of).toList(), shortAnswerQuestion.getSimilarityValue(),
                shortAnswerQuestion.matchLetterCase());
    }
}
