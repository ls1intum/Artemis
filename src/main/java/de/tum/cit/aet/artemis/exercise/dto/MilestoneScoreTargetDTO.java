package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One student's aggregated score on one milestone exercise, identified for recomputation.
 * <p>
 * Produced by {@code MilestoneExerciseGroupRepository.findMilestoneScoreTargetsForUserStoryResultsModifiedAfter} as the
 * fallback sweep of {@code MilestoneScoreScheduleService}: it names the pairs whose user story results changed, without
 * loading the results themselves.
 *
 * @param milestoneExerciseId the id of the milestone exercise whose aggregate is out of date
 * @param studentId           the id of the student whose score to recompute
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneScoreTargetDTO(Long milestoneExerciseId, Long studentId) {
}
