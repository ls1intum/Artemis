package de.tum.cit.aet.artemis.iris.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether the Iris (Pyris) service is currently reachable, for the admin ingestion dashboard health tile. When Iris is
 * unreachable, dispatched lecture ingestions cannot run, which is the single most important thing for an admin to see
 * when nothing is being ingested.
 *
 * @param reachable whether the last Iris health check reported the service as up
 */
@Schema(description = "Reachability of the Iris service for the ingestion dashboard")
public record PyrisReachabilityDTO(boolean reachable) {
}
