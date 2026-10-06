package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One instructor course of one user, as selected for a whole batch of requesters at once. The user id is what the
 * rows are grouped by, so it is not part of the {@link CourseRequestInstructorCourseDTO} sent to the client.
 *
 * @param userId    the id of the user who is an instructor of the course
 * @param id        the id of the course
 * @param title     the title of the course
 * @param shortName the short name of the course
 * @param semester  the semester of the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestInstructorCourseRowDTO(Long userId, Long id, String title, String shortName, String semester) {

    public CourseRequestInstructorCourseDTO toCourse() {
        return new CourseRequestInstructorCourseDTO(id, title, shortName, semester);
    }
}
