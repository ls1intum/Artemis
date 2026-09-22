package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One Pyris ingestion worker, seen from the leases it currently holds.
 * <p>
 * Artemis has no worker registry: a worker exists here exactly as long as it holds at least one lease,
 * because {@code locked_by} on the claimed rows is the only place its identity is recorded.
 *
 * @param bootId          the worker's boot id
 * @param activeRuns      how many runs it currently holds
 * @param lastHeartbeatAt the most recent lease renewal across its runs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionWorkerDTO(String bootId, long activeRuns, @Nullable ZonedDateTime lastHeartbeatAt) {
}
