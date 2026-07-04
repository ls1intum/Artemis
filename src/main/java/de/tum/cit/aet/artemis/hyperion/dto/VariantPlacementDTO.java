package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Placement choice for the generated variant (plan Section 5.1) — applied server-side in FINALIZING;
 * the client only refreshes (Section 5.3, point 4).
 *
 * @param type            the placement kind
 * @param existingGroupId required iff type == EXISTING_GROUP
 * @param newGroup        required iff type == NEW_GROUP
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantPlacementDTO(PlacementType type, @Nullable Long existingGroupId, @Nullable NewGroupDTO newGroup) implements Serializable {

    /**
     * EXISTING_GROUP(groupId) | NEW_GROUP(fields) | STANDALONE | SAME_EXAM_GROUP (implicit/forced for exam
     * exercises — the wizard skips the placement step, plan Section 5.5).
     */
    public enum PlacementType {
        EXISTING_GROUP, NEW_GROUP, STANDALONE, SAME_EXAM_GROUP
    }

    /**
     * Fields for creating a new variant group during FINALIZING — mirrors the wizard's new-group step
     * (see exercise-variant-ai-modal-wizard.component.ts newGroup* signals).
     *
     * TODO (Sonnet): Align field set + types with the existing group-creation endpoint's request DTO
     * (ExerciseVariantGroupResource) so the finalizer can delegate without mapping gymnastics: title,
     * maxPoints, releaseDate, startDate, dueDate, assessmentDueDate (ZonedDateTime).
     */
    public record NewGroupDTO(String title) implements Serializable {
    }

    // TODO (Sonnet): Cross-field validation (resource-level or Jakarta @AssertTrue): EXISTING_GROUP requires
    // existingGroupId, NEW_GROUP requires newGroup with non-blank title; SAME_EXAM_GROUP is REJECTED for
    // non-exam exercises and FORCED (any other value → 400 or silently overridden — pick 400) for exam
    // exercises (plan Section 5.5).
}
