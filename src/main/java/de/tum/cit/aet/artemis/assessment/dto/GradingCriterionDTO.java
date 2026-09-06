package de.tum.cit.aet.artemis.assessment.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

// structuredGradingInstructions is a List, not a Set: GradingInstructionDTO is a record whose value equality spans all
// components including the nullable id, so two value-identical new instructions (id == null) would collapse in a Set on
// both mapping and Jackson deserialization. The entity path never collapses them (DomainObject.equals is false when an
// id is null), so a Set here would silently drop rubric rows. A List preserves every instruction.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingCriterionDTO(Long id, String title, List<GradingInstructionDTO> structuredGradingInstructions) {

    /**
     * Convert GradingCriterion to GradingCriterionDTO. Used in the exercise DTOs for Athena.
     *
     * @param gradingCriterion GradingCriterion to convert
     * @return a GradingCriterionDTO based on the GradingCriterion
     */
    public static GradingCriterionDTO of(@NotNull GradingCriterion gradingCriterion) {
        List<GradingInstructionDTO> instructions = gradingCriterion.getStructuredGradingInstructions() == null ? List.of()
                : gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).toList();
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(), instructions);
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
