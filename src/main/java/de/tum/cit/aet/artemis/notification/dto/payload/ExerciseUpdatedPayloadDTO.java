package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the exercise updated notification.
 *
 * @param exerciseId       the exercise involved
 * @param exerciseTitle    the title of that exercise
 * @param examId           the exam the exercise belongs to, absent for a course exercise
 * @param exerciseGroupId  the exam exercise group, when the exercise belongs to an exam
 * @param exerciseType     the kind of exercise, which decides where a client links to
 * @param notificationText the message the editor wrote about the update, absent when they wrote none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseUpdatedPayloadDTO(@Nullable Long exerciseId, @Nullable String exerciseTitle, @Nullable Long examId, @Nullable Long exerciseGroupId,
        @Nullable String exerciseType, @Nullable String notificationText) implements CourseNotificationPayloadDTO {
}
