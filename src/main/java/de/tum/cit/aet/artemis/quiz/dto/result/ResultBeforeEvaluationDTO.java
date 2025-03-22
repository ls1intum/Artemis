package de.tum.cit.aet.artemis.quiz.dto.result;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionBeforeEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultBeforeEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean rated, QuizSubmissionBeforeEvaluationDTO submission) {

    public static ResultBeforeEvaluationDTO of(Result result) {
        QuizSubmissionBeforeEvaluationDTO quizSubmission = null;
        if (result.getSubmission() instanceof QuizSubmission submission) {
            quizSubmission = QuizSubmissionBeforeEvaluationDTO.of(submission);
        }
        return new ResultBeforeEvaluationDTO(result.getId(), result.getCompletionDate(), result.isRated(), quizSubmission);
    }

}
