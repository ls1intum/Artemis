package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One object actually stored in the {@code SearchableEntities} Weaviate collection, for the admin course-content
 * browser: its type, entity id, display title, when it was ingested (the Weaviate object creation time), and its full
 * stored property map so an admin can see the actual data Weaviate holds for the object.
 *
 * @param type       the entity type discriminator
 * @param entityId   the entity id
 * @param title      the indexed title, or null when absent
 * @param ingestedAt the ISO-8601 time the object was created in Weaviate, or null when unavailable
 * @param properties the full property map stored in Weaviate for this object (every field and its value)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "An object stored in the SearchableEntities Weaviate collection, with its full property map")
public record IndexedEntityDTO(String type, long entityId, @Nullable String title, @Nullable String ingestedAt, Map<String, Object> properties) {
}
