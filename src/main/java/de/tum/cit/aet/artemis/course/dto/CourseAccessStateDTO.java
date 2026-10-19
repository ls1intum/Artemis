package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether the requesting user already has access to a course.
 * <p>
 * The enrollment page asks this to decide between showing the enrollment form and redirecting into the course. It used
 * to answer the question by requesting the whole course dashboard and checking whether that came back 403, which meant
 * loading every exercise, participation, submission and result of the course in order to discard all of it.
 *
 * @param hasAccess true if the user is at least a student in the course, or an administrator
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAccessStateDTO(boolean hasAccess) {
}
