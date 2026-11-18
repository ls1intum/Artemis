package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;

/**
 * Quiz-submission-specific content carried in the conduction / summary payload (unwrapped into the submission object).
 * During a fresh conduction there are no submitted answers and no score; on resume / after submission they carry the
 * student's answers, and once results are published the per-answer {@code scoreInPoints} and the nested quiz question's
 * solutions become visible.
 * <p>
 * The answers are projected via {@link SubmittedAnswerAfterEvaluationDTO}, which faithfully mirrors whatever the exam
 * masking pipeline left on the entity: before publish the nested question's solutions/mappings are already stripped, so
 * nothing is re-added here.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmissionFieldsForConductionDTO(Double scoreInPoints, List<SubmittedAnswerAfterEvaluationDTO> submittedAnswers) {

    /**
     * Converts a QuizSubmission's score and submitted answers, guarding against uninitialized lazy collections.
     *
     * @param quizSubmission the quiz submission to convert
     * @return the quiz-submission-specific fields
     */
    public static QuizSubmissionFieldsForConductionDTO of(QuizSubmission quizSubmission) {
        var answers = quizSubmission.getSubmittedAnswers();
        List<SubmittedAnswerAfterEvaluationDTO> submittedAnswers = (answers == null || !Hibernate.isInitialized(answers) || answers.isEmpty()) ? null
                : answers.stream().map(SubmittedAnswerAfterEvaluationDTO::of).toList();
        return new QuizSubmissionFieldsForConductionDTO(quizSubmission.getScoreInPoints(), submittedAnswers);
    }
}
