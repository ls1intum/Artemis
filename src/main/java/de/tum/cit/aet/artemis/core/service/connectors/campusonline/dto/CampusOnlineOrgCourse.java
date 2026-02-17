package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlineOrgCourse(@JacksonXmlProperty(localName = "courseID") String courseId, @JacksonXmlProperty(localName = "courseName") String courseName,
        @JacksonXmlProperty(localName = "teachingTerm") String teachingTerm) {
}
