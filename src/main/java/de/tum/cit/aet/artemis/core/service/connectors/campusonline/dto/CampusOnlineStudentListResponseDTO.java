package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the CAMPUSOnline student list XML response (/cdm/course/students/xml endpoint).
 * Contains a flat list of person elements representing students enrolled in a course.
 *
 * @param persons the list of enrolled students (may be null if the course has no students)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineStudentListResponseDTO(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "person") List<CampusOnlinePersonDTO> persons) {
}
