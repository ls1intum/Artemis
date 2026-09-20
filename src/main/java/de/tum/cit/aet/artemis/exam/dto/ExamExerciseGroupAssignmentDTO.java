package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for moving an exam exercise into a different exercise group of the same exam. Unlike course exercise
 * variant groups, exam exercises must always belong to exactly one group, so (unlike
 * {@code ExerciseVariantGroupAssignmentDTO}) the target group is required.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamExerciseGroupAssignmentDTO(@NotNull Long exerciseGroupId) {
}
