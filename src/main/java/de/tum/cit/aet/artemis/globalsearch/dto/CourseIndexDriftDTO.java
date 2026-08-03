package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Read-only, computed-live index drift for one course: for each entity type, how many rows are indexed
 * in Weaviate versus how many should be. Nothing is persisted; this is recomputed on every request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Per-type indexed vs expected drift for a course")
public record CourseIndexDriftDTO(@Schema(description = "The course id") long courseId, @Schema(description = "Drift per entity type") List<TypeDriftDTO> types) {
}
