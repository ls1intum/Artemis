package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingCriterionDTO(long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

    /**
     * Convert GradingCriterion to GradingCriterionDTO. Used in the exercise DTOs for Athena.
     *
     * @param gradingCriterion GradingCriterion to convert
     * @return a GradingCriterionDTO based on the GradingCriterion
     */
    public static GradingCriterionDTO of(@NotNull GradingCriterion gradingCriterion) {
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(),
                gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).collect(Collectors.toSet()));
    }

    public static GradingCriterion toEntity(@NotNull GradingCriterionDTO dto) {
        GradingCriterion criterion = new GradingCriterion();
        criterion.setId(dto.id());
        criterion.setTitle(dto.title());

        if (dto.structuredGradingInstructions() != null && !dto.structuredGradingInstructions().isEmpty()) {
            Set<GradingInstruction> instructions = dto.structuredGradingInstructions().stream().map(GradingInstructionDTO::toEntity).collect(Collectors.toSet());
            criterion.setStructuredGradingInstructions(instructions);
        }

        return criterion;
    }
}
