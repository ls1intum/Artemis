package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata describing a lecture unit citation referenced in an Iris response.
 *
 * @param entityId         the id of the referenced lecture unit
 * @param lectureTitle     the title of the lecture containing the unit
 * @param lectureUnitTitle the title of the lecture unit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCitationMetaDTO(long entityId, String lectureTitle, String lectureUnitTitle) {
}
