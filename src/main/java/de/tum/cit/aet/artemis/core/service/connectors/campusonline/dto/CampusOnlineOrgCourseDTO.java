package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO for a single course element within the CAMPUSOnline organization courses XML response.
 *
 * @param courseId     the unique CAMPUSOnline course identifier
 * @param courseName   the official course title
 * @param teachingTerm the semester/teaching term (e.g. "2025W")
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgCourseDTO(@JacksonXmlProperty(localName = "courseID") String courseId, @JacksonXmlProperty(localName = "courseName") String courseName,
        @JacksonXmlProperty(localName = "teachingTerm") String teachingTerm) {
}
