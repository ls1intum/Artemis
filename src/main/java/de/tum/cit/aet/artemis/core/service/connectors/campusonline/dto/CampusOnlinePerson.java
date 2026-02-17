package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlinePerson(@JacksonXmlProperty(isAttribute = true, localName = "ident") String ident, @JacksonXmlProperty(localName = "name") CampusOnlinePersonName name,
        @JacksonXmlProperty(localName = "contactData") CampusOnlineContactData contactData, @JacksonXmlProperty(localName = "extension") CampusOnlineExtension extension,
        @JacksonXmlProperty(localName = "attendance") CampusOnlineAttendance attendance) {
}
