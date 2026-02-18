package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineExtensionDTO(@JacksonXmlProperty(localName = "registrationNumber") String registrationNumber) {
}
