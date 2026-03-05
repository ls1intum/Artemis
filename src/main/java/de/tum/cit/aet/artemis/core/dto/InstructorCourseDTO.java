package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight DTO for displaying an instructor's existing courses in the course request accept modal.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InstructorCourseDTO(String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate) {
}
