package de.tum.cit.aet.artemis.service.dto;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.GradingCriterion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingCriterionDTO(long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

    /**
     * Convert GradingCriterion to GradingCriterionDTO. Used in the exercise DTOs for Athena.
     *
     * @param gradingCriterion GradingCriterion to convert
     * @return GradingCriterionDTO
     */
    public static GradingCriterionDTO of(@NotNull GradingCriterion gradingCriterion) {
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(),
                gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).collect(Collectors.toSet()));
    }
}
