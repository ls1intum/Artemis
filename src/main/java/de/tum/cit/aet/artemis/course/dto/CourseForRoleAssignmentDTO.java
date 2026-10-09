package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal course information for choosing a course in which an administrator assigns a course role to a user.
 *
 * @param id        the id of the course
 * @param title     the title of the course
 * @param shortName the short name of the course
 * @param semester  the semester of the course, if any
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForRoleAssignmentDTO(long id, @Nullable String title, @Nullable String shortName, @Nullable String semester) {
}
