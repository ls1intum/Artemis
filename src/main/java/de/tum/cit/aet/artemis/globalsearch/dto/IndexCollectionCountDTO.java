package de.tum.cit.aet.artemis.globalsearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The number of objects currently stored in a single Weaviate collection.
 * <p>
 * {@code available} is {@code false} when the collection could not be read (for example it does not
 * exist yet because nothing of that kind has been ingested); {@code objectCount} is then {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Object count for a single Weaviate collection")
public record IndexCollectionCountDTO(@Schema(description = "The Weaviate collection name") String collection,
        @Schema(description = "Number of objects stored in the collection, or null when unavailable") Long objectCount,
        @Schema(description = "Whether the collection could be read") boolean available) {

    /**
     * @param collection  the Weaviate collection name
     * @param objectCount the number of objects currently stored
     * @return an available count
     */
    public static IndexCollectionCountDTO available(String collection, long objectCount) {
        return new IndexCollectionCountDTO(collection, objectCount, true);
    }

    /**
     * @param collection the Weaviate collection name
     * @return an unavailable count (collection missing or unreadable)
     */
    public static IndexCollectionCountDTO unavailable(String collection) {
        return new IndexCollectionCountDTO(collection, null, false);
    }
}
