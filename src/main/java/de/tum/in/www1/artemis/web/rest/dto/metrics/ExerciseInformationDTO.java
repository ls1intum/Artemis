package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * DTO for exercise information.
 *
 * @param id        the id of the exercise
 * @param shortName shortTitle the short title of the exercise
 * @param title     title the title of the exercise
 * @param start     the start date of the exercise
 * @param due       the due date of the exercise
 * @param type      the type of the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseInformationDTO(long id, String shortName, String title, ZonedDateTime start, ZonedDateTime due, Class<? extends Exercise> type) {

    public static <E extends Exercise> ExerciseInformationDTO of(E exercise) {
        var startDate = exercise.getStartDate();
        if (startDate == null) {
            startDate = exercise.getReleaseDate();
        }
        return new ExerciseInformationDTO(exercise.getId(), exercise.getShortName(), exercise.getTitle(), startDate, exercise.getDueDate(), exercise.getClass());
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (other instanceof ExerciseInformationDTO otherDTO) {
            return id == otherDTO.id && shortName.equals(otherDTO.shortName) && title.equals(otherDTO.title) && start.isEqual(otherDTO.start) && due.isEqual(otherDTO.due)
                    && type.equals(otherDTO.type);
        }

        return false;
    }
}
