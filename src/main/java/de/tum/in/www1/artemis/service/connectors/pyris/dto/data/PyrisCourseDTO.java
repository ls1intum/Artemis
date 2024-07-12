package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseDTO(long id, String name, String description) {

    public PyrisCourseDTO(Course course) {
        this(course.getId(), course.getTitle(), course.getDescription());
    }
}
