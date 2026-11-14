package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Metadata holding the lecture name and the lecture unit name of a lecture unit id in a citation used in an Iris response.
 *
 * @param entityId         the id of the cited lecture unit
 * @param lectureTitle     the title of the lecture containing the unit
 * @param lectureUnitTitle the title of the lecture unit
 * @param lectureId        the id of the lecture containing the unit
 * @param courseId         the id of the course containing the lecture
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCitationMetaDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long entityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String lectureTitle, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String lectureUnitTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lectureId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long courseId) {
}
