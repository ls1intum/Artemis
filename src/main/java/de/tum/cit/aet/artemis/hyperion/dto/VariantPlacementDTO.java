package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;

/**
 * Placement choice for the generated variant (plan Section 5.1) — applied server-side in FINALIZING;
 * the client only refreshes (Section 5.3, point 4).
 *
 * @param type            the placement kind
 * @param existingGroupId required iff type == EXISTING_GROUP
 * @param newGroup        required iff type == NEW_GROUP — the same payload as the group-creation endpoint
 *                            (title, maxPoints, shared timeline dates), so the wizard's new-group form maps 1:1 and
 *                            the finalizer can delegate to the existing group-creation service path without mapping.
 *                            Deliberately NOT annotated {@code @Nullable}: springdoc pushes a $ref property's null
 *                            type into the shared component schema, corrupting CreateExerciseVariantGroup to
 *                            {@code type: 'null'} in the generated spec. Optionality is expressed via the missing
 *                            {@code required} entry instead; the resource validates presence per placement type.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantPlacementDTO(PlacementType type, @Nullable Long existingGroupId, CreateExerciseVariantGroupDTO newGroup) implements Serializable {

    /**
     * EXISTING_GROUP(groupId) | NEW_GROUP(fields) | STANDALONE | SAME_EXAM_GROUP (implicit/forced for exam
     * exercises — the wizard skips the placement step, plan Section 5.5).
     */
    public enum PlacementType {
        EXISTING_GROUP, NEW_GROUP, STANDALONE, SAME_EXAM_GROUP
    }
}
