package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the build at the start date of an exercise that allows repository access before it reads off a graded participation to decide whether it has to be built.
 * <p>
 * The participation has to be built if the head of its repository moved past the setup commit and differs from the commit of its newest submission. Only the participations
 * that pass this check are loaded as entities.
 *
 * @param participationId  the participation
 * @param repositoryUri    the assignment repository, null if it was never created
 * @param branch           the branch of the assignment repository, null if the default branch applies
 * @param latestCommitHash the commit of the newest submission of the participation, null if it has none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StartDateBuildCandidateDTO(long participationId, @Nullable String repositoryUri, @Nullable String branch, @Nullable String latestCommitHash) {
}
