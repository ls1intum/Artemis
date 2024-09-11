package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * DTO containing {@link Course} information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForImportDTO(long id, String title, String shortName, String semester) {

    public CourseForImportDTO(Course course) {
        this(course.getId(), course.getTitle(), course.getShortName(), course.getSemester());
    }
}
