package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for {@code POST exercises/{exerciseId}/struggle-intervention/cancel}.
 *
 * @param requestToken the client-minted UUID that identifies the specific struggle request to cancel;
 *                         matches {@link de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob#requestToken()}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CancelStruggleJobRequestDTO(String requestToken) {
}
