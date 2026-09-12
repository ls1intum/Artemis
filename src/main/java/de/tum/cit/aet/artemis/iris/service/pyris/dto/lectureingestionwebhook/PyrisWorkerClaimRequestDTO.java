package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A pulling Pyris worker's request for new ingestion jobs.
 *
 * @param bootId  boot id of the requesting worker process; recorded on every claim as the lease holder
 * @param maxJobs how many jobs the worker can take right now (its free capacity)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWorkerClaimRequestDTO(String bootId, int maxJobs) {
}
