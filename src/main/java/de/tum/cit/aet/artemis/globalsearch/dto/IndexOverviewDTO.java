package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The top-band overview of the search index for the admin ingestion-observability dashboard: whether Weaviate is
 * reachable and at which address, whether the Iris module is enabled, and the live object count of each tracked
 * collection.
 *
 * @param weaviateReachable whether the Weaviate instance is currently reachable
 * @param weaviateAddress   the configured Weaviate address (shown whether or not it is reachable)
 * @param irisEnabled       whether the Iris module is enabled (the Iris content collections only exist when it is)
 * @param irisReachable     whether Iris is actually answering; always {@code false} when the module is disabled
 * @param collections       the per-collection live object counts
 */
public record IndexOverviewDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean weaviateReachable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String weaviateAddress, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean irisEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean irisReachable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IndexedCollectionCountDTO> collections) {
}
