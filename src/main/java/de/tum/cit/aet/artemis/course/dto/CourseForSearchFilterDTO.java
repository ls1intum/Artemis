package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A course the global search {@code course:} filter can offer.
 *
 * @param id     the id of the course
 * @param title  the title of the course
 * @param active whether the course has not ended yet; a course that has not started, or has no end date, counts as
 *                   active, since the people who can see it before its start are the staff preparing it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForSearchFilterDTO(long id, String title, boolean active) {
}
