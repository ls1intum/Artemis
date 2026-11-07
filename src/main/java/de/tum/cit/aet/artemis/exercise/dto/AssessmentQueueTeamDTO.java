package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identifies the team of a participation displayed in the assessment queue, without exposing its members.
 *
 * @param participationId the participation id
 * @param id              the team id used for the team details link
 * @param shortName       the team short name
 * @param submission      the submission displayed in the queue
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentQueueTeamDTO(Long participationId, Long id, String shortName, SubmissionResponseDTO submission) {
}
