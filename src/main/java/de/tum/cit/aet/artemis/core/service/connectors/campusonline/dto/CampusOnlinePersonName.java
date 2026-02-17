package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlinePersonName(@JacksonXmlProperty(localName = "given") String given, @JacksonXmlProperty(localName = "family") String family) {
}
