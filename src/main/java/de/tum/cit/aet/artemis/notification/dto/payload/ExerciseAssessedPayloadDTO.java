package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the exercise assessed notification.
 *
 * @param exerciseId     the exercise involved
 * @param exerciseTitle  the title of that exercise
 * @param exerciseType   the kind of exercise, which decides where a client links to
 * @param numberOfPoints the points the exercise is worth
 * @param score          the score the student reached
 * @param examId         the exam the exercise belongs to, absent for a course exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseAssessedPayloadDTO(Long exerciseId, String exerciseTitle, String exerciseType, Long numberOfPoints, Long score, Long examId)
        implements CourseNotificationPayloadDTO {
}
