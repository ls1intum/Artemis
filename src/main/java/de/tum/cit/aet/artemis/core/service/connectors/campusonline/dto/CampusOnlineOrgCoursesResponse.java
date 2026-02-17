package de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record CampusOnlineOrgCoursesResponse(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "course") List<CampusOnlineOrgCourse> courses) {
}
