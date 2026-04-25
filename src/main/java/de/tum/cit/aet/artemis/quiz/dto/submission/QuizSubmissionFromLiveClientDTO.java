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
 * type, the database ids, and (optionally) the existing submission id. All other client-supplied fields
 * are silently ignored, eliminating the "stale id from a tab opened before a quiz re-import" failure mode
 * (#12584) that previously required the post-bind {@code sanitizeSubmittedAnswersAgainstQuestions} workaround.
 * <p>
 * The optional {@code id} field is preserved through the build because test-exam quiz auto-saves rely on
 * the client echoing back the existing submission id so that successive PUTs to {@code /submissions/exam}
 * UPDATE the row instead of INSERTing a new one (since {@code preventMultipleSubmissions} intentionally
 * returns early for test exams). Live mode and regular-exam mode overwrite this id with the
 * server-resolved one, so a client-supplied value cannot leak across users on those paths.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuizSubmissionFromLiveClientDTO(Long id, Set<@Valid SubmittedAnswerFromLiveClientDTO> submittedAnswers) {
}
