package de.tum.cit.aet.artemis.exam.dto;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * DTO for importing exercise groups.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupImportDTO(@Nullable String title, boolean isMandatory, @Nullable List<ExerciseImportDTO> exercises) {

    /**
     * Creates an ExerciseGroupImportDTO from an existing ExerciseGroup entity.
     *
     * @param group the exercise group to convert
     * @return the DTO representation
     */
    public static ExerciseGroupImportDTO of(ExerciseGroup group) {
        List<ExerciseImportDTO> exerciseDTOs = null;
        if (group.getExercises() != null && !group.getExercises().isEmpty()) {
            exerciseDTOs = group.getExercises().stream().map(ExerciseImportDTO::of).toList();
        }
        return new ExerciseGroupImportDTO(group.getTitle(), group.getIsMandatory(), exerciseDTOs);
    }

    /**
     * Creates a new ExerciseGroup entity from this DTO.
     *
     * @return a new ExerciseGroup entity
     */
    public ExerciseGroup toEntity() {
        ExerciseGroup group = new ExerciseGroup();
        group.setTitle(title);
        group.setIsMandatory(isMandatory);

        // Add exercises
        if (exercises != null) {
            for (ExerciseImportDTO exerciseDTO : exercises) {
                Exercise exercise = exerciseDTO.toEntity();
                if (exercise != null) {
                    group.addExercise(exercise);
                }
            }
        }

        return group;
    }

    /**
     * Gets the list of exercises or an empty list if null.
     *
     * @return the exercises or empty list
     */
    public List<ExerciseImportDTO> exercisesOrEmpty() {
        return exercises != null ? exercises : new ArrayList<>();
    }
}
