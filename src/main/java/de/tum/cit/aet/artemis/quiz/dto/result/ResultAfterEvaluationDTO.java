package de.tum.cit.aet.artemis.quiz.dto.result;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionAfterEvaluationDTO;

public record ResultAfterEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, QuizSubmissionAfterEvaluationDTO submission,
        AssessmentType assessmentType) {

    public static ResultAfterEvaluationDTO of(Result result) {
        QuizSubmissionAfterEvaluationDTO quizSubmission = null;
        if (result.getSubmission() instanceof QuizSubmission submission) {
            quizSubmission = QuizSubmissionAfterEvaluationDTO.of(submission);
        }
        return new ResultAfterEvaluationDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), quizSubmission,
                result.getAssessmentType());
    }

}
