package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the programming test cases changed notification.
 *
 * @param exerciseId      the exercise involved
 * @param exerciseTitle   the title of that exercise
 * @param examId          the exam the exercise belongs to, absent for a course exercise
 * @param exerciseGroupId the exam exercise group, when the exercise belongs to an exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingTestCasesChangedPayloadDTO(@Nullable Long exerciseId, @Nullable String exerciseTitle, @Nullable Long examId, @Nullable Long exerciseGroupId)
        implements CourseNotificationPayloadDTO {
}
