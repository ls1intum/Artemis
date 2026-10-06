package de.tum.cit.aet.artemis.core.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CourseRole;

/**
 * One role a user holds in one course, with the minimal course information needed to display and link it.
 * A user holding several roles in a course is represented by one DTO per role.
 *
 * @param courseId        the id of the course
 * @param courseTitle     the title of the course
 * @param courseShortName the short name of the course
 * @param courseSemester  the semester of the course, if any
 * @param role            the role the user holds in the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseRoleDTO(long courseId, @Nullable String courseTitle, @Nullable String courseShortName, @Nullable String courseSemester, CourseRole role) {
}
