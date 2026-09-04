package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the quiz exercise started notification.
 *
 * @param exerciseId    the exercise involved
 * @param exerciseTitle the title of that exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseStartedPayload(Long exerciseId, String exerciseTitle) implements CourseNotificationPayload {
}
