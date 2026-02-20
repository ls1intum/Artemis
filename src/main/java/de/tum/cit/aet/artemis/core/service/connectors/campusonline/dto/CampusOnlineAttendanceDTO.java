package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the attendance element in CAMPUSOnline student XML responses.
 * The confirmed attribute indicates whether the student's enrollment is confirmed ("J" = yes).
 *
 * @param confirmed the confirmation status attribute ("J" for confirmed, other values for unconfirmed)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineAttendanceDTO(@JacksonXmlProperty(isAttribute = true, localName = "confirmed") String confirmed) {
}
