package de.tum.in.www1.artemis.service.dto;

import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.GradingCriterion;

public record GradingCriterionDTO(long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

    /**
     * Convert GradingCriterion to GradingCriterionDTO
     *
     * @param gradingCriterion GradingCriterion to convert
     * @return GradingCriterionDTO
     */
    public static GradingCriterionDTO of(GradingCriterion gradingCriterion) {
        if (gradingCriterion == null) {
            return null;
        }
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(),
                gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).collect(Collectors.toSet()));
    }
}
