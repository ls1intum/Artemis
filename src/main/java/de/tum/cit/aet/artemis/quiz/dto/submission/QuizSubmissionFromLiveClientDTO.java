package de.tum.cit.aet.artemis.quiz.dto.submission;

import java.util.Set;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

/**
 * Request DTO for the live, exam, and training quiz submission endpoints.
 * <p>
 * Mirrors the rich entity-shaped JSON the existing TypeScript client posts (a full {@code QuizSubmission}
 * with nested {@code SubmittedAnswer}, {@code AnswerOption}, {@code DragItem}, {@code DropLocation},
 * {@code ShortAnswerSpot} objects), but binds only the data the server actually needs: the discriminator
 * type and the database ids. All other client-supplied fields are silently ignored, eliminating the
 * "stale id from a tab opened before a quiz re-import" failure mode (#12584) that previously required
 * the post-bind {@code sanitizeSubmittedAnswersAgainstQuestions} workaround.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuizSubmissionFromLiveClientDTO(Set<@Valid SubmittedAnswerFromLiveClientDTO> submittedAnswers) {
}
