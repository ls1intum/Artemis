package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata holding the lecture name and the lecture unit name of a lecture unit id in a citation used in an Iris response.
 *
 * @param entityId         the id of the cited lecture unit
 * @param lectureTitle     the title of the lecture containing the unit
 * @param lectureUnitTitle the title of the lecture unit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCitationMetaDTO(long entityId, @NotNull String lectureTitle, @NotNull String lectureUnitTitle) {
}
