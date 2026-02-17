package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlineAttendance(@JacksonXmlProperty(isAttribute = true, localName = "confirmed") String confirmed) {
}
