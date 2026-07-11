package de.tum.cit.aet.artemis.exam.dto.submit;

import java.util.Set;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

/**
 * The quiz-submission variant of {@link SubmitExamSubmissionDTO}: carries the existing submission id and the
 * student's submitted answers.
 * <p>
 * The answer set reuses the {@link SubmittedAnswerFromLiveClientDTO} family shipped for the live and exam quiz
 * auto-save endpoints (#12832): the server rebuilds the {@link de.tum.cit.aet.artemis.quiz.domain.QuizSubmission}
 * via {@link de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService#buildSubmissionFromLiveClientDTO}, which
 * re-resolves every answer's ids against the loaded quiz exercise and leniently drops answers whose ids no longer
 * exist. This is intentionally stricter than the previous exam-submit path (which persisted client-supplied ids
 * verbatim) and keeps the exam hand-in consistent with the quiz live-save behavior.
 *
 * @param id               the id of the existing quiz submission the answers belong to
 * @param submittedAnswers the student's submitted answers (may be {@code null} or empty)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuizExamSubmissionDTO(Long id, Set<@Valid SubmittedAnswerFromLiveClientDTO> submittedAnswers) implements SubmitExamSubmissionDTO {
}
