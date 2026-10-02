package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The least that identifies a {@link Course} to somebody choosing one from a list.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseSelectionDTO(long id, String title, String shortName, String semester) {
}
