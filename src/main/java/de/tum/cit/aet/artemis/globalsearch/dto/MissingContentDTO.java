package de.tum.cit.aet.artemis.globalsearch.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One lecture unit that should have ingested content but does not: it has a PDF attachment with no slides indexed, or a
 * video source with no transcript indexed.
 * <p>
 * A unit can appear twice, once per kind, when it has both a PDF and a video and neither has been ingested.
 *
 * @param lectureUnitId the database id of the lecture unit
 * @param title         the unit's name, or {@code null} if it could no longer be resolved
 * @param kind          which content is absent: {@code slides} or {@code transcript}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MissingContentDTO(long lectureUnitId, @Nullable @Schema(nullable = true) String title, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
        "slides", "transcript" }) String kind){
}
