package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionWithoutMappingDTO(List<ShortAnswerSpotDTO> spots, List<ShortAnswerSolutionDTO> solutions, Integer similarityValue, Boolean matchLetterCase) {

    public static ShortAnswerQuestionWithoutMappingDTO of(ShortAnswerQuestion question) {
        return new ShortAnswerQuestionWithoutMappingDTO(question.getSpots().stream().map(ShortAnswerSpotDTO::of).toList(),
                question.getSolutions().stream().map(ShortAnswerSolutionDTO::of).toList(), question.getSimilarityValue(), question.getMatchLetterCase());
    }

}
