package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the CAMPUSOnline organization courses XML response (/xcal/organization/courses/xml endpoint).
 * Contains a flat list of courses (no wrapper element) for a given organizational unit and date range.
 *
 * @param courses the list of courses in the organizational unit (may be null if no courses found)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgCoursesResponseDTO(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "course") List<CampusOnlineOrgCourseDTO> courses) {
}
