package de.tum.cit.aet.artemis.quiz.dto.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithSolutionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionAfterEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultAfterEvaluationWithSubmissionDTO(@JsonUnwrapped ResultAfterEvaluationDTO resultAfterEvaluationDTO, QuizSubmissionForResultDTO submission) {

    public static ResultAfterEvaluationWithSubmissionDTO of(Result result) {
        return new ResultAfterEvaluationWithSubmissionDTO(ResultAfterEvaluationDTO.of(result), QuizSubmissionForResultDTO.of((QuizSubmission) result.getSubmission()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizSubmissionForResultDTO(@JsonUnwrapped QuizSubmissionAfterEvaluationDTO quizSubmissionAfterEvaluationDTO, StudentQuizParticipationWithSolutionsDTO participation) {

    public static QuizSubmissionForResultDTO of(QuizSubmission submission) {
        return new QuizSubmissionForResultDTO(QuizSubmissionAfterEvaluationDTO.of(submission),
                StudentQuizParticipationWithSolutionsDTO.of((StudentParticipation) submission.getParticipation()));
    }

}
