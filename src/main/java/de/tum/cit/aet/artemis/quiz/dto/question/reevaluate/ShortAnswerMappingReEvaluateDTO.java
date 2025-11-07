package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingReEvaluateDTO(Long solutionId, Long solutionTempID, @NotNull Long spotId) {

    public static ShortAnswerMappingReEvaluateDTO of(ShortAnswerMapping shortAnswerMapping) {
        return new ShortAnswerMappingReEvaluateDTO(shortAnswerMapping.getSolution().getId(), shortAnswerMapping.getSolution().getTempID(), shortAnswerMapping.getSpot().getId());
    }
}
