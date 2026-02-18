package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlinePersonDTO(@JacksonXmlProperty(isAttribute = true, localName = "ident") String ident,
        @JacksonXmlProperty(localName = "name") CampusOnlinePersonNameDTO name, @JacksonXmlProperty(localName = "contactData") CampusOnlineContactDataDTO contactData,
        @JacksonXmlProperty(localName = "extension") CampusOnlineExtensionDTO extension, @JacksonXmlProperty(localName = "attendance") CampusOnlineAttendanceDTO attendance) {
}
