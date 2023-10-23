package de.tum.in.www1.artemis.service.dto;

import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.GradingCriterion;

public record GradingGriterionDTO(long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

    public static GradingGriterionDTO of(GradingCriterion gradingCriterion) {
        return new GradingGriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(),
                gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).collect(Collectors.toSet()));
    }
}
