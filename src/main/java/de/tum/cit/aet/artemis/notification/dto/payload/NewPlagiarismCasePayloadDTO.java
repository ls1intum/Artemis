package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the new plagiarism case notification.
 *
 * @param exerciseId          the exercise involved
 * @param exerciseTitle       the title of that exercise
 * @param exerciseType        the kind of exercise, which decides where a client links to
 * @param postMarkdownContent the content of the post, as markdown
 * @param examId              the exam the exercise belongs to, absent for a course exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NewPlagiarismCasePayloadDTO(@Nullable Long exerciseId, @Nullable String exerciseTitle, @Nullable String exerciseType, @Nullable String postMarkdownContent,
        @Nullable Long examId) implements CourseNotificationPayloadDTO {
}
