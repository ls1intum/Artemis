package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Request DTO for creating a new exercise group.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} lets the existing Angular client keep posting a full
 * entity-shaped body (it sets {@code exam}, and possibly {@code id}/{@code exercises}); those extra properties are
 * silently ignored, so no client change is required. The target exam is taken exclusively from the path, which is why
 * this DTO carries neither an id nor an exam reference (the former id-must-be-null and exam-id-consistency checks are
 * therefore moot).
 *
 * @param title       the title of the new exercise group (may be blank / omitted)
 * @param isMandatory whether the exercise group must be included when generating student exams; the client always sends
 *                        this (the create form defaults it to {@code true}), so a primitive is safe here
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseGroupCreateDTO(@Nullable String title, boolean isMandatory) {

    /**
     * Builds a create DTO from an existing exercise group (used to construct request bodies).
     *
     * @param exerciseGroup the exercise group to convert
     * @return the create DTO carrying its title and mandatory flag
     */
    public static ExerciseGroupCreateDTO of(ExerciseGroup exerciseGroup) {
        return new ExerciseGroupCreateDTO(exerciseGroup.getTitle(), Boolean.TRUE.equals(exerciseGroup.getIsMandatory()));
    }

    /**
     * Builds a new (transient) {@link ExerciseGroup} entity from this DTO.
     *
     * @return the new exercise group entity, without an exam back-reference (set by the caller)
     */
    public ExerciseGroup toEntity() {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle(title);
        exerciseGroup.setIsMandatory(isMandatory);
        return exerciseGroup;
    }
}
