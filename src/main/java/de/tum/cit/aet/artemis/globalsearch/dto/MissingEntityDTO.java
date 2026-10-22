package de.tum.cit.aet.artemis.globalsearch.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entity the database expects to be indexed but that the index does not hold, named so an admin can act on it.
 * <p>
 * The title is resolved only for entities that are actually missing, never for the whole course, because the point of
 * the list is to name a usually small gap rather than to enumerate a course.
 *
 * @param type      the {@code SearchableEntitySchema.TypeValues} discriminator
 * @param entityId  the database id of the missing entity
 * @param title     the entity's title or name, or {@code null} if it could no longer be resolved
 * @param lectureId the parent lecture, set for lecture units and {@code null} for every other type. The browser needs
 *                      it to place a unit the index does not hold under the lecture it belongs to; without it, content
 *                      stored for such a unit is indistinguishable from content whose unit the database has lost
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MissingEntityDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type, long entityId, @Nullable @Schema(nullable = true) String title,
        @Nullable @Schema(nullable = true) Long lectureId) {
}
