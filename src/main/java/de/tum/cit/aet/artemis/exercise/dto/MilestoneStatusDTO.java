package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether the requesting student has started the {@code MilestoneExercise} anchoring a {@code MilestoneExerciseGroup} -
 * the milestone itself is never shown to students (see {@code MilestoneExercise.isVisibleToStudents}), so the group view
 * needs this narrow status instead of the full exercise to decide whether to offer "Start exercise" or a "Code" button
 * for the shared repository.
 *
 * @param milestoneExerciseId the id of the group's anchor milestone exercise
 * @param started             whether the requesting student already has a participation in it
 * @param participationId     the id of that participation, or {@code null} if not started
 * @param repositoryUri       the participation's (shared) repository URI, or {@code null} if not started
 * @param problemStatement    the milestone exercise's problem statement, which doubles as the group's description in the
 *                                student group view - the milestone itself is never rendered, so this endpoint is the only
 *                                way to reach it; {@code null} when the instructor left it empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneStatusDTO(long milestoneExerciseId, boolean started, @Nullable Long participationId, @Nullable String repositoryUri, @Nullable String problemStatement) {
}
