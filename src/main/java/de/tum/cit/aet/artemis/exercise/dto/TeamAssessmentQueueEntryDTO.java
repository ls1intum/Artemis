package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Database-selected team queue entry, without submission histories.
 *
 * @param participationId the participation id
 * @param teamId          the team id
 * @param shortName       the team short name
 * @param submissionId    the latest submission with a result in the requested round, or null
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamAssessmentQueueEntryDTO(Long participationId, Long teamId, String shortName, Long submissionId) {
}
