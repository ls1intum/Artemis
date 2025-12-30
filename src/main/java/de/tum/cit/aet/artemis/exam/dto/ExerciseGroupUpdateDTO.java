package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * DTO for updating exercise groups.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupUpdateDTO(@NotNull Long id, @Nullable String title, @NotNull Boolean isMandatory) {

    /**
     * Creates an ExerciseGroupUpdateDTO from the given ExerciseGroup domain object.
     *
     * @param exerciseGroup the exercise group to convert
     * @return the corresponding DTO
     */
    public static ExerciseGroupUpdateDTO of(ExerciseGroup exerciseGroup) {
        return new ExerciseGroupUpdateDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory());
    }

    /**
     * Applies the DTO values to an existing ExerciseGroup entity.
     * This updates the managed entity with values from the DTO.
     *
     * @param exerciseGroup the existing exercise group to update
     */
    public void applyTo(ExerciseGroup exerciseGroup) {
        exerciseGroup.setTitle(title);
        exerciseGroup.setIsMandatory(isMandatory);
    }
}
