package de.tum.cit.aet.artemis.quiz.dto.submission;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.result.ResultAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmissionAfterEvaluationDTO(Long id, String submissionExerciseType, Boolean submitted, SubmissionType type, ZonedDateTime submissionDate, Double scoreInPoints,
        Set<SubmittedAnswerAfterEvaluationDTO> submittedAnswers, List<ResultAfterEvaluationDTO> results) {

    /**
     * Creates a {@link QuizSubmissionAfterEvaluationDTO} from the given {@link QuizSubmission} entity.
     * <p>
     * If the submission's results or submitted answers are not initialized or are null,
     * the corresponding DTO fields will be empty. Otherwise, they are mapped to their
     * respective DTO representations.
     *
     * @param submission the quiz submission entity to convert, must not be null
     * @return a DTO containing the evaluated submission data
     */
    public static QuizSubmissionAfterEvaluationDTO of(QuizSubmission submission) {
        List<ResultAfterEvaluationDTO> results = null;
        Set<SubmittedAnswerAfterEvaluationDTO> submittedAnswers = Set.of();
        if (Hibernate.isInitialized(submission.getResults()) && submission.getResults() != null) {
            results = submission.getResults().stream().map(ResultAfterEvaluationDTO::of).toList();
        }
        if (Hibernate.isInitialized(submission.getSubmittedAnswers()) && submission.getSubmittedAnswers() != null) {
            submittedAnswers = submission.getSubmittedAnswers().stream().map(SubmittedAnswerAfterEvaluationDTO::of).collect(Collectors.toSet());
        }
        return new QuizSubmissionAfterEvaluationDTO(submission.getId(), submission.getSubmissionExerciseType(), submission.isSubmitted(), submission.getType(),
                submission.getSubmissionDate(), submission.getScoreInPoints(), submittedAnswers, results);
    }

}
