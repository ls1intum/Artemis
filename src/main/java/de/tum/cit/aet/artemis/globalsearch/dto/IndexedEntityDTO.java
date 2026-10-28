package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row stored in the {@code SearchableEntities} collection for a course, reduced to what the browser's tree needs to
 * place it.
 * <p>
 * Deliberately not the full property map. That map carries {@code description}, which holds problem statements, lecture
 * descriptions and post bodies, so returning it for every row of a course meant shipping the course's entire body text
 * to draw a list of titles. The stored record is read for a single entity when one is actually selected.
 *
 * @param type       the {@code SearchableEntitySchema.TypeValues} discriminator
 * @param entityId   the database id of the entity this row represents
 * @param title      the stored title, or {@code null} if the row has none
 * @param lectureId  the parent lecture, set on lecture units and {@code null} otherwise; the tree nests units by it
 * @param ingestedAt when Weaviate created the object, or {@code null} if the creation time could not be read
 * @param expected   whether the database still expects this metadata identity; {@code false} labels a retained row as orphaned
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IndexedEntityDTO(String type, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long entityId, @Nullable @Schema(nullable = true) String title,
        @Nullable @Schema(nullable = true) Long lectureId, @Nullable @Schema(nullable = true) Instant ingestedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean expected) {
}
