package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for representing archived courses from previous semesters.
 *
 * @param id       The id of the course
 * @param title    The title of the course
 * @param semester The semester in which the course was offered
 * @param color    The background color of the course
 * @param icon     The icon of the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForArchiveDTO(long id, String title, String semester, String color, String icon) {
}
