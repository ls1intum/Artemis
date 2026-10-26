package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;

// structuredGradingInstructions is a List, not a Set: GradingInstructionDTO is a record whose value equality spans all
// components including the nullable id, so two value-identical new instructions (id == null) would collapse in a Set on
// both mapping and Jackson deserialization. The entity path never collapses them (DomainObject.equals is false when an
// id is null), so a Set here would silently drop rubric rows. A List preserves every instruction.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GradingCriterionDTO(Long id, String title, List<GradingInstructionDTO> structuredGradingInstructions) {

    /**
     * Convert GradingCriterion to GradingCriterionDTO. Used in the exercise DTOs for Athena.
     *
     * @param gradingCriterion GradingCriterion to convert
     * @return a GradingCriterionDTO based on the GradingCriterion
     */
    public static GradingCriterionDTO of(@NotNull GradingCriterion gradingCriterion) {
        List<GradingInstructionDTO> instructions = gradingCriterion.getStructuredGradingInstructions() == null ? List.of()
                : gradingCriterion.getStructuredGradingInstructions().stream().sorted(
                        Comparator.comparingLong(GradingInstruction::getSortOrder).thenComparing(GradingInstruction::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(GradingInstructionDTO::of).toList();
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(), instructions);
    }

    /**
     * Converts loaded rubric rows in their persisted order, retaining duplicate-valued rows in exported payloads.
     *
     * @param criteria the initialized criteria
     * @return the criteria in instructor order, with legacy ties resolved by row id
     */
    public static List<GradingCriterionDTO> ofAll(Collection<GradingCriterion> criteria) {
        return criteria.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(GradingCriterion::getSortOrder).thenComparing(GradingCriterion::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(GradingCriterionDTO::of).toList();
    }

    /**
     * Creates ordered domain rows, assigning positions from the request array rather than accepting client positions.
     *
     * @param criteria requested rubric rows, or null when absent
     * @return new criteria retaining all rows and their request order
     */
    public static Set<GradingCriterion> toEntities(Collection<GradingCriterionDTO> criteria) {
        Set<GradingCriterion> entities = new LinkedHashSet<>();
        if (criteria != null) {
            long position = 0;
            for (GradingCriterionDTO dto : criteria) {
                GradingCriterion criterion = dto.toEntity();
                criterion.setSortOrder(position++);
                entities.add(criterion);
            }
        }
        return entities;
    }

    /**
     * Returns the same criterion without the row ids, its own and those of its instructions, for payloads that are
     * written to a file and read back by another instance, whose import copies them onto the rubric it creates.
     *
     * @return a copy of this DTO with {@code null} ids
     */
    public GradingCriterionDTO withoutIds() {
        List<GradingInstructionDTO> instructions = structuredGradingInstructions == null ? null
                : structuredGradingInstructions.stream().map(GradingInstructionDTO::withoutId).toList();
        return new GradingCriterionDTO(null, title, instructions);
    }

    /**
     * Converts this DTO into a {@link GradingCriterion} entity.
     *
     * @return a new {@link GradingCriterion} with values copied from the DTO
     */
    public GradingCriterion toEntity() {
        GradingCriterion criterion = new GradingCriterion();
        criterion.setId(this.id);
        applyTo(criterion);
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
            existing = new LinkedHashSet<>();
            gradingCriterion.setStructuredGradingInstructions(existing);
        }

        Map<Long, GradingInstruction> existingById = existing.stream().filter(i -> i.getId() != null).collect(Collectors.toMap(GradingInstruction::getId, i -> i));

        Set<GradingInstruction> updatedInstructions = new LinkedHashSet<>();
        long position = 0;

        for (GradingInstructionDTO instructionDTO : this.structuredGradingInstructions) {
            GradingInstruction instruction = instructionDTO.id() != null ? existingById.get(instructionDTO.id()) : null;

            if (instruction == null) {
                instruction = instructionDTO.toEntity();
                instruction.setGradingCriterion(gradingCriterion);
            }
            else {
                instructionDTO.applyTo(instruction);
            }
            instruction.setSortOrder(position++);
            updatedInstructions.add(instruction);
        }
        // Keep Hibernate's orphan-removal collection and existing instruction instances, which feedback references.
        existing.clear();
        existing.addAll(updatedInstructions);
    }
}
