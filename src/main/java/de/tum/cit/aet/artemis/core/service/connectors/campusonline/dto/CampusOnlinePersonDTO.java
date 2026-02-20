package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for a person element in CAMPUSOnline student list XML responses.
 * Represents a student enrolled in a course, with nested contact data, extension info, and attendance status.
 *
 * @param ident       the person's unique identifier attribute in CAMPUSOnline
 * @param name        the person's name (given and family)
 * @param contactData the person's contact data (email), may be null
 * @param extension   university-specific extension data (registration number), may be null
 * @param attendance  the attendance/enrollment status, may be null
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlinePersonDTO(@JacksonXmlProperty(isAttribute = true, localName = "ident") String ident,
        @JacksonXmlProperty(localName = "name") CampusOnlinePersonNameDTO name, @JacksonXmlProperty(localName = "contactData") CampusOnlineContactDataDTO contactData,
        @JacksonXmlProperty(localName = "extension") CampusOnlineExtensionDTO extension, @JacksonXmlProperty(localName = "attendance") CampusOnlineAttendanceDTO attendance) {
}
