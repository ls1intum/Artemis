package de.tum.cit.aet.artemis.globalsearch.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One lecture unit that is expected to have ingested content of a given kind (slide chunks from a PDF, or transcript
 * segments from a video) but for which that content is absent from the Iris collections, for the admin course-content
 * browser. It lets an admin see exactly which units still need their content ingested, by name and kind, rather than
 * only a course-level PDF/video count.
 *
 * @param lectureUnitId the database id of the lecture unit with the gap
 * @param title         the unit name, or null when it could not be resolved
 * @param kind          the missing content kind: {@code slides} (a PDF with no slide chunks) or {@code transcript} (a
 *                          video with no transcript segments)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A lecture unit expected to have ingested content of a kind that is absent from the Iris collections")
public record MissingContentDTO(long lectureUnitId, @Nullable String title, String kind) {
}
