package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionAfterDTO(@JsonUnwrapped ShortAnswerQuestionDuringDTO shortAnswerQuestionDuringDTO, List<ShortAnswerMappingDTO> correctMappings) {

    public static ShortAnswerQuestionAfterDTO of(ShortAnswerQuestion shortAnswerQuestion) {
        return new ShortAnswerQuestionAfterDTO(ShortAnswerQuestionDuringDTO.of(shortAnswerQuestion),
                shortAnswerQuestion.getCorrectMappings().stream().map(ShortAnswerMappingDTO::of).toList());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ShortAnswerMappingDTO(Long id, Integer shortAnswerSpotIndex, Integer shortAnswerSolutionIndex, Boolean invalid, ShortAnswerSolutionDTO solution,
            ShortAnswerSpotDTO spot) {

        public static ShortAnswerMappingDTO of(ShortAnswerMapping shortAnswerMapping) {
            return new ShortAnswerMappingDTO(shortAnswerMapping.getId(), shortAnswerMapping.getShortAnswerSpotIndex(), shortAnswerMapping.getShortAnswerSolutionIndex(),
                    shortAnswerMapping.isInvalid(), ShortAnswerSolutionDTO.of(shortAnswerMapping.getSolution()), ShortAnswerSpotDTO.of(shortAnswerMapping.getSpot()));
        }

    }

}
