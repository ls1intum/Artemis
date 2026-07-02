package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Minimal reference to an {@link ExerciseVariantGroup} embedded inside exercise DTOs. Only carries the fields the
 * client needs to determine group membership and display the group name — avoids loading the full exercise collection.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVariantGroupReferenceDTO(Long id, String title, @Nullable Double maxPoints) {

    public static ExerciseVariantGroupReferenceDTO of(ExerciseVariantGroup group) {
        return new ExerciseVariantGroupReferenceDTO(group.getId(), group.getTitle(), group.getMaxPoints());
    }
}
