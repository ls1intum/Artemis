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
 * @param maxPoints the maximum achievable points of the exercise
 * @param type      the type of the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseInformationDTO(long id, String shortName, String title, ZonedDateTime start, ZonedDateTime due, Double maxPoints, Class<? extends Exercise> type) {

    /**
     * Create a new ExerciseInformationDTO from an exercise.
     *
     * @param exercise the exercise to create the DTO from
     * @return the new ExerciseInformationDTO
     */
    public static <E extends Exercise> ExerciseInformationDTO of(E exercise) {
        var startDate = exercise.getStartDate();
        if (startDate == null) {
            startDate = exercise.getReleaseDate();
        }
        return new ExerciseInformationDTO(exercise.getId(), exercise.getShortName(), exercise.getTitle(), startDate, exercise.getDueDate(), exercise.getMaxPoints(),
                exercise.getClass());
    }

    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (other instanceof ExerciseInformationDTO otherDTO) {
            // Compare all fields for equality, for dates the isEqual method is used to compare the date and time
            return id == otherDTO.id && shortName.equals(otherDTO.shortName) && title.equals(otherDTO.title) && start.isEqual(otherDTO.start) && due.isEqual(otherDTO.due)
                    && maxPoints.equals(otherDTO.maxPoints) && type.equals(otherDTO.type);
        }

        return false;
    }
}
