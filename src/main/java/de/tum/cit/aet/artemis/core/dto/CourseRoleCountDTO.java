package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CourseRole;

/**
 * DTO representing the count of users with a specific role in a course.
 *
 * @param courseId the id of the course
 * @param role     the role of the users
 * @param count    the number of users with the role
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRoleCountDTO(long courseId, CourseRole role, long count) {
}
