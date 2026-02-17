package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlineCourseMetadataResponse(@JacksonXmlProperty(localName = "courseName") String courseName,
        @JacksonXmlProperty(localName = "teachingTerm") String teachingTerm, @JacksonXmlProperty(localName = "courseLanguage") String courseLanguage,
        @JacksonXmlProperty(localName = "courseID") String courseId) {
}
