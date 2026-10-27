package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the quiz exercise started notification.
 *
 * @param exerciseId    the exercise involved
 * @param exerciseTitle the title of that exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseStartedPayloadDTO(@Nullable Long exerciseId, @Nullable String exerciseTitle) implements CourseNotificationPayloadDTO {
}
