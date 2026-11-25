package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The assignment repository of one student participation, together with the id that the cleanup of old checkouts continues from.
 *
 * @param participationId the id of the participation, which orders the batches and is where the next batch starts after
 * @param repositoryUri   the assignment repository of the participation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationRepositoryUriDTO(long participationId, String repositoryUri) {
}
