package de.tum.cit.aet.artemis.globalsearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entity the database expects to be indexed but that the index does not hold, named so an admin can act on it.
 * <p>
 * The title is resolved only for entities that are actually missing, never for the whole course, because the point of
 * the list is to name a usually small gap rather than to enumerate a course.
 *
 * @param type     the {@code SearchableEntitySchema.TypeValues} discriminator
 * @param entityId the database id of the missing entity
 * @param title    the entity's title or name, or {@code null} if it could no longer be resolved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MissingEntityDTO(String type, long entityId, String title) {
}
