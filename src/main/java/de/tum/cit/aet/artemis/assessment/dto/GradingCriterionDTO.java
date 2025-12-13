package de.tum.cit.aet.artemis.assessment.dto;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingCriterionDTO(@NotNull Long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

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
            Set<GradingInstruction> instructions = this.structuredGradingInstructions.stream().map(GradingInstructionDTO::toEntity).collect(Collectors.toSet());
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

        Set<GradingInstruction> existing = gradingCriterion.getStructuredGradingInstructions();
        if (existing == null) {
            existing = new HashSet<>();
        }

        Map<Long, GradingInstruction> existingById = existing.stream().filter(i -> i.getId() != null).collect(Collectors.toMap(GradingInstruction::getId, i -> i));

        Set<GradingInstruction> updatedInstructions = new HashSet<>();

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
