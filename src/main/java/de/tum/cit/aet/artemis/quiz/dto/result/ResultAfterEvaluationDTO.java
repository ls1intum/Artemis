package de.tum.cit.aet.artemis.quiz.dto.result;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionAfterEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultAfterEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, QuizSubmissionAfterEvaluationDTO submission,
        AssessmentType assessmentType) {

    /**
     * Creates a ResultAfterEvaluationDTO object from a Result object.
     *
     * @param result the Result object
     * @return the created ResultAfterEvaluationDTO object
     */
    public static ResultAfterEvaluationDTO of(Result result) {
        QuizSubmissionAfterEvaluationDTO quizSubmission = null;
        if (result.getSubmission() instanceof QuizSubmission submission) {
            quizSubmission = QuizSubmissionAfterEvaluationDTO.of(submission);
        }
        return new ResultAfterEvaluationDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), quizSubmission,
                result.getAssessmentType());
    }

}
