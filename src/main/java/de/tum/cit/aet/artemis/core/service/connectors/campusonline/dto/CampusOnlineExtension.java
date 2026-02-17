package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlineExtension(@JacksonXmlProperty(localName = "registrationNumber") String registrationNumber) {
}
