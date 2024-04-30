package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exercise information.
 *
 * @param id        the id of the exercise
 * @param shortName shortTitle the short title of the exercise
 * @param title     title the title of the exercise
 * @param start     the start date of the exercise
 * @param due       the due date of the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseInformationDTO(long id, String shortName, String title, ZonedDateTime start, ZonedDateTime due) {
}
