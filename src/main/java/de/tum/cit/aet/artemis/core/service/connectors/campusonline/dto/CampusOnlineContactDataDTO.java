package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the contactData element in CAMPUSOnline person XML responses.
 *
 * @param email the student's email address from CAMPUSOnline
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineContactDataDTO(@JacksonXmlProperty(localName = "email") String email) {
}
