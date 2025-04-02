package de.tum.cit.aet.artemis.quiz.dto.submission;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmissionAfterEvaluationDTO(Long id, String submissionExerciseType, Boolean submitted, SubmissionType type, ZonedDateTime submissionDate, Double scoreInPoints,
        Set<SubmittedAnswerAfterEvaluationDTO> submittedAnswers) {

    public static QuizSubmissionAfterEvaluationDTO of(QuizSubmission submission) {
        return new QuizSubmissionAfterEvaluationDTO(submission.getId(), submission.getSubmissionExerciseType(), submission.isSubmitted(), submission.getType(),
                submission.getSubmissionDate(), submission.getScoreInPoints(),
                submission.getSubmittedAnswers().stream().map(SubmittedAnswerAfterEvaluationDTO::of).collect(Collectors.toSet()));
    }

}
