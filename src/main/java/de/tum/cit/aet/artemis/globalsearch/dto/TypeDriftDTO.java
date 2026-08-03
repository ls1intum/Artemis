package de.tum.cit.aet.artemis.globalsearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Index drift for a single entity type within a course: how many rows are actually indexed in Weaviate
 * ({@code present}) versus how many should be ({@code expected}).
 * <p>
 * {@code expected} is {@code null} for types whose expected count is not computed yet (their course
 * linkage needs dedicated handling); those rows still report {@code present}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Indexed vs expected count for one entity type in a course")
public record TypeDriftDTO(@Schema(description = "The entity type discriminator") String type,
        @Schema(description = "Rows currently indexed in Weaviate for this type and course") long present,
        @Schema(description = "Rows that should be indexed, or null when not computed for this type") Long expected) {
}
