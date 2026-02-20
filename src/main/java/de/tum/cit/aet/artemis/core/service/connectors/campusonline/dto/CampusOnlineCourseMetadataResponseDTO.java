package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for the CAMPUSOnline course metadata XML response (CDM /cdm/course/xml endpoint).
 * Contains the core metadata fields returned when fetching details for a single course.
 * All fields may be null if the CAMPUSOnline API omits them from the response.
 *
 * @param courseName     the official course title
 * @param teachingTerm   the semester/teaching term (e.g. "2025W")
 * @param courseLanguage the course language code (e.g. "DE", "EN")
 * @param courseId       the unique CAMPUSOnline course identifier
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineCourseMetadataResponseDTO(@JacksonXmlProperty(localName = "courseName") String courseName,
        @JacksonXmlProperty(localName = "teachingTerm") String teachingTerm, @JacksonXmlProperty(localName = "courseLanguage") String courseLanguage,
        @JacksonXmlProperty(localName = "courseID") String courseId) {
}
