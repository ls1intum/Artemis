package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionWithoutMappingDTO(List<ShortAnswerSpotDTO> spots, List<ShortAnswerSolutionDTO> solutions, Integer similarityValue, Boolean matchLetterCase) {

    /**
     * Creates a DTO from the given question, normalizing empty lists to null for consistent JSON serialization.
     *
     * @param question the short answer question
     * @return the DTO
     */
    public static ShortAnswerQuestionWithoutMappingDTO of(ShortAnswerQuestion question) {
        var spots = question.getSpots().stream().map(ShortAnswerSpotDTO::of).toList();
        var solutions = question.getSolutions().stream().map(ShortAnswerSolutionDTO::of).toList();
        // Use null for empty lists to match JSON round-trip behavior with @JsonInclude(NON_EMPTY)
        return new ShortAnswerQuestionWithoutMappingDTO(spots.isEmpty() ? null : spots, solutions.isEmpty() ? null : solutions, question.getSimilarityValue(),
                question.getMatchLetterCase());
    }

}
