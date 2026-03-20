package de.tum.cit.aet.artemis.exam.dto;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

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
        List<ExerciseImportDTO> exerciseDTOs = Optional.ofNullable(group.getExercises()).filter(exs -> !exs.isEmpty()).map(exs -> exs.stream().map(ExerciseImportDTO::of).toList())
                .orElse(null);
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
        exercisesOrEmpty().stream().map(ExerciseImportDTO::toEntity).filter(Objects::nonNull).forEach(group::addExercise);

        return group;
    }

    /**
     * Gets the list of exercises or an empty list if null.
     *
     * @return the exercises or empty list
     */
    public List<ExerciseImportDTO> exercisesOrEmpty() {
        return Objects.requireNonNullElse(exercises, Collections.emptyList());
    }
}
