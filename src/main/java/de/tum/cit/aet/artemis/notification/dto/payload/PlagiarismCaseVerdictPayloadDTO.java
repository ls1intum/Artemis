package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the plagiarism case verdict notification.
 *
 * @param exerciseId    the exercise involved
 * @param exerciseTitle the title of that exercise
 * @param exerciseType  the kind of exercise, which decides where a client links to
 * @param verdict       the verdict reached in the plagiarism case
 * @param examId        the exam the exercise belongs to, absent for a course exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseVerdictPayloadDTO(Long exerciseId, String exerciseTitle, String exerciseType, String verdict, Long examId) implements CourseNotificationPayloadDTO {
}
