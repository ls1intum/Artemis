package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the extension element in CAMPUSOnline person XML responses.
 * Contains university-specific extended data such as the student's registration number (Matrikelnummer).
 *
 * @param registrationNumber the student's university registration number
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineExtensionDTO(@JacksonXmlProperty(localName = "registrationNumber") String registrationNumber) {
}
