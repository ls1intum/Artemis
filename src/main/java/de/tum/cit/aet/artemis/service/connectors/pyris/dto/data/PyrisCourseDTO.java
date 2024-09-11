package de.tum.cit.aet.artemis.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseDTO(long id, String name, String description) {

    public PyrisCourseDTO(Course course) {
        this(course.getId(), course.getTitle(), course.getDescription());
    }
}
