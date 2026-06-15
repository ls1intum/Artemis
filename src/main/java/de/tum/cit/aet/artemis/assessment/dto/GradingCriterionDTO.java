package de.tum.cit.aet.artemis.assessment.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingCriterionDTO(Long id, String title, List<GradingInstructionDTO> structuredGradingInstructions) {

    /**
     * Convert GradingCriterion to GradingCriterionDTO. Used in the exercise DTOs for Athena.
     *
     * @param gradingCriterion GradingCriterion to convert
     * @return a GradingCriterionDTO based on the GradingCriterion
     */
    public static GradingCriterionDTO of(@NotNull GradingCriterion gradingCriterion) {
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(),
                gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).toList());
    }

    /**
     * Converts this DTO into a {@link GradingCriterion} entity.
     *
     * @return a new {@link GradingCriterion} with values copied from the DTO
     */
    public GradingCriterion toEntity() {
        GradingCriterion criterion = new GradingCriterion();
        criterion.setId(this.id);
        criterion.setTitle(this.title);
        if (this.structuredGradingInstructions != null && !this.structuredGradingInstructions.isEmpty()) {
            List<GradingInstruction> instructions = this.structuredGradingInstructions.stream().map(GradingInstructionDTO::toEntity).toList();
            criterion.setStructuredGradingInstructions(instructions);
        }
        return criterion;
    }

    /**
     * Applies this DTO's data to an existing managed {@link GradingCriterion}.
     * Intended for update scenarios within a persistence context.
     */
    public void applyTo(GradingCriterion gradingCriterion) {
        if (this.title != null) {
            gradingCriterion.setTitle(this.title);
        }

        if (this.structuredGradingInstructions == null) {
            return;
        }

        List<GradingInstruction> existing = gradingCriterion.getStructuredGradingInstructions();
        if (existing == null) {
            existing = new ArrayList<>();
        }

        Map<Long, GradingInstruction> existingById = existing.stream().filter(i -> i.getId() != null).collect(Collectors.toMap(GradingInstruction::getId, i -> i));

        List<GradingInstruction> updatedInstructions = new ArrayList<>();

        for (GradingInstructionDTO instructionDTO : this.structuredGradingInstructions) {
            GradingInstruction instruction = instructionDTO.id() != null ? existingById.get(instructionDTO.id()) : null;

            if (instruction == null) {
                instruction = instructionDTO.toEntity();
                instruction.setGradingCriterion(gradingCriterion);
            }
            else {
                instructionDTO.applyTo(instruction);
            }
            updatedInstructions.add(instruction);
        }
        gradingCriterion.setStructuredGradingInstructions(updatedInstructions);
    }
}
