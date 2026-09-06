package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the programming build run update notification.
 *
 * @param exerciseId      the exercise involved
 * @param exerciseTitle   the title of that exercise
 * @param examId          the exam the exercise belongs to, absent for a course exercise
 * @param exerciseGroupId the exam exercise group, when the exercise belongs to an exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingBuildRunUpdatePayloadDTO(Long exerciseId, String exerciseTitle, Long examId, Long exerciseGroupId) implements CourseNotificationPayloadDTO {
}
