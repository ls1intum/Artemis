package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A course in which the requester of a course request holds the instructor role.
 *
 * @param id        the id of the course
 * @param title     the title of the course
 * @param shortName the short name of the course
 * @param semester  the semester of the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestInstructorCourseDTO(Long id, String title, String shortName, String semester) {
}
