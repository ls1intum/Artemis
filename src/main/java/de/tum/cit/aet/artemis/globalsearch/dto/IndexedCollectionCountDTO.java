package de.tum.cit.aet.artemis.globalsearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The live object count of one indexed Weaviate collection, for the dashboard overview. A collection that cannot be read
 * (not present on this instance, or unreachable) is reported with {@code readable = false} and a {@code null} count rather
 * than failing the whole overview.
 *
 * @param collection the collection name as addressed (the prefixed Artemis collection or an exact Iris collection name)
 * @param count      the number of objects in the collection, or {@code null} if it could not be read
 * @param readable   whether the count could be read
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndexedCollectionCountDTO(String collection, Long count, boolean readable) {

    public static IndexedCollectionCountDTO of(String collection, long count) {
        return new IndexedCollectionCountDTO(collection, count, true);
    }

    public static IndexedCollectionCountDTO unavailable(String collection) {
        return new IndexedCollectionCountDTO(collection, null, false);
    }
}
