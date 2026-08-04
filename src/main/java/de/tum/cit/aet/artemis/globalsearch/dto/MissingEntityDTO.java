package de.tum.cit.aet.artemis.globalsearch.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entity that the database expects to be indexed for a course but that is absent from the {@code SearchableEntities}
 * Weaviate collection, for the admin course-content browser. It lets an admin see exactly which items were never
 * ingested (or dropped), by name, rather than only a missing count.
 *
 * @param type     the entity type discriminator (matches the census type, e.g. {@code exercise}, {@code lecture_unit})
 * @param entityId the database id of the missing entity
 * @param title    the display title of the missing entity, or null when it could not be resolved
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "An entity expected by the database but not present in the SearchableEntities Weaviate collection")
public record MissingEntityDTO(String type, long entityId, @Nullable String title) {
}
