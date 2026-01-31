package de.tum.cit.aet.artemis.iris.dto;

/**
 * Metadata describing a lecture unit citation referenced in an Iris response.
 *
 * @param entityId         the id of the referenced lecture unit
 * @param lectureTitle     the title of the lecture containing the unit (may be null or blank)
 * @param lectureUnitTitle the title of the lecture unit (may be null or blank)
 */
public record IrisCitationMetaDTO(long entityId, String lectureTitle, String lectureUnitTitle) {
}
