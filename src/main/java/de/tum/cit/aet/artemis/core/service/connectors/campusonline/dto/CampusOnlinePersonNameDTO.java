package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the name element in CAMPUSOnline person XML responses.
 *
 * @param given  the person's given (first) name
 * @param family the person's family (last) name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlinePersonNameDTO(@JacksonXmlProperty(localName = "given") String given, @JacksonXmlProperty(localName = "family") String family) {
}
