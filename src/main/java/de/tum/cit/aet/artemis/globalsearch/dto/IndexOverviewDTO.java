package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A read-only snapshot of the Weaviate index for the admin Course Content Ingestion Dashboard.
 * <p>
 * This is the minimal first slice of the dashboard data: the reachability of the vector database and
 * the object count of each collection that global search and the Iris tutor rely on. Per-course
 * completeness is added later once the reconcile sync-state exists.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Snapshot of Weaviate health and per-collection object counts")
public record IndexOverviewDTO(@Schema(description = "Whether Weaviate is currently reachable") boolean weaviateUp,
        @Schema(description = "The configured Weaviate address") String weaviateAddress,
        @Schema(description = "Object count per Weaviate collection") List<IndexCollectionCountDTO> collections,
        @Schema(description = "Whether the Iris integration is configured, so the dashboard can hide Iris-only sections when it is not") boolean irisEnabled) {
}
