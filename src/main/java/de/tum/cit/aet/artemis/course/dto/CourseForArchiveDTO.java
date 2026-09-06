package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for representing archived courses from previous semesters.
 *
 * @param id         The id of the course
 * @param title      The title of the course
 * @param semester   The semester in which the course was offered
 * @param color      The background color of the course
 * @param icon       The icon of the course
 * @param testCourse Whether the course is a test course
 * @param canManage  Whether the current user can manage the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForArchiveDTO(long id, String title, String semester, String color, String icon, boolean testCourse, boolean canManage) {
}
