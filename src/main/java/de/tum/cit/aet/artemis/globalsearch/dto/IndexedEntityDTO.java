package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One object actually stored in the {@code SearchableEntities} collection for a course, for the admin content browser.
 * <p>
 * {@code properties} is the row exactly as Weaviate holds it, minus the fields it has no value for: the schema is a wide
 * sparse superset shared by all entity types, so most of it is absent for any given row and shipping those keys would be
 * noise. What remains is the stored record the browser renders field by field.
 *
 * @param type       the {@code SearchableEntitySchema.TypeValues} discriminator
 * @param entityId   the database id of the entity this row represents
 * @param title      the stored title, or {@code null} if the row has none
 * @param ingestedAt when Weaviate created the object, or {@code null} if the creation time could not be read
 * @param properties the populated stored properties
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IndexedEntityDTO(String type, long entityId, String title, Instant ingestedAt, Map<String, Object> properties) {
}
