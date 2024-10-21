package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseDTO(long id, String name, String description) {

    public static PyrisCourseDTO from(Course course) {
        return new PyrisCourseDTO(course.getId(), course.getTitle(), course.getDescription());
    }
}
