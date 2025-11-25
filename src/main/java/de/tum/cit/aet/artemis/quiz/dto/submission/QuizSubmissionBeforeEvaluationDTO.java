package de.tum.cit.aet.artemis.quiz.dto.submission;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerBeforeEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmissionBeforeEvaluationDTO(Long id, String submissionExerciseType, Boolean submitted, SubmissionType type, ZonedDateTime submissionDate,
        Set<SubmittedAnswerBeforeEvaluationDTO> submittedAnswers) {

    public static QuizSubmissionBeforeEvaluationDTO of(QuizSubmission submission) {
        return new QuizSubmissionBeforeEvaluationDTO(submission.getId(), submission.getSubmissionExerciseType(), submission.isSubmitted(), submission.getType(),
                submission.getSubmissionDate(), submission.getSubmittedAnswers().stream().map(SubmittedAnswerBeforeEvaluationDTO::of).collect(Collectors.toSet()));
    }

}
