package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the new manual feedback request notification.
 *
 * @param exerciseId    the exercise involved
 * @param exerciseTitle the title of that exercise
 * @param examId        the exam the exercise belongs to, absent for a course exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NewManualFeedbackRequestPayloadDTO(Long exerciseId, String exerciseTitle, Long examId) implements CourseNotificationPayloadDTO {
}
