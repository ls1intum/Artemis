package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

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
 * @param collectionPrefix  the prefix this installation actually resolves its own collections with, or empty when none
 *                              is configured. Shown because several installations can share one Weaviate cluster and
 *                              are kept apart only by this differing between them; two servers showing the same value
 *                              are reading and overwriting each other's entities, which is otherwise invisible here
 * @param baseUrl           this installation's own address, which is what separates its rows inside the Iris content
 *                              collections (those are addressed by their exact name, so the prefix does not apply)
 */
public record IndexOverviewDTO(boolean weaviateReachable, String weaviateAddress, boolean irisEnabled, boolean irisReachable, List<IndexedCollectionCountDTO> collections,
        String collectionPrefix, String baseUrl) {
}
