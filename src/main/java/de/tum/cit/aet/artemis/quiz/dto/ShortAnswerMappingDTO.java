package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingDTO(Long id, Integer shortAnswerSpotIndex, Integer shortAnswerSolutionIndex, Boolean invalid, ShortAnswerSolutionDTO solution,
        ShortAnswerSpotDTO spot) {

    public static ShortAnswerMappingDTO of(ShortAnswerMapping mapping) {
        return new ShortAnswerMappingDTO(mapping.getId(), mapping.getShortAnswerSpotIndex(), mapping.getShortAnswerSolutionIndex(), mapping.isInvalid(),
                ShortAnswerSolutionDTO.of(mapping.getSolution()), ShortAnswerSpotDTO.of(mapping.getSpot()));
    }

}
