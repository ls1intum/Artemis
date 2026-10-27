package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

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
public record ProgrammingBuildRunUpdatePayloadDTO(@Nullable Long exerciseId, @Nullable String exerciseTitle, @Nullable Long examId, @Nullable Long exerciseGroupId)
        implements CourseNotificationPayloadDTO {
}
