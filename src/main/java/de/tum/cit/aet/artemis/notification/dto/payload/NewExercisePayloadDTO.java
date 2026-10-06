package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the new exercise notification.
 *
 * @param exerciseId     the exercise involved
 * @param exerciseTitle  the title of that exercise
 * @param difficulty     the difficulty of the exercise
 * @param releaseDate    when the exercise becomes available
 * @param dueDate        when the exercise is due
 * @param numberOfPoints the points the exercise is worth
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NewExercisePayloadDTO(Long exerciseId, String exerciseTitle, String difficulty, String releaseDate, String dueDate, Long numberOfPoints)
        implements CourseNotificationPayloadDTO {
}
