package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgCoursesResponseDTO(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "course") List<CampusOnlineOrgCourseDTO> courses) {
}
