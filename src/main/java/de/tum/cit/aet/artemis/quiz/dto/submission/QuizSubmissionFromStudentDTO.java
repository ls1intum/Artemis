package de.tum.cit.aet.artemis.quiz.dto.submission;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromStudentDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmissionFromStudentDTO(@NotNull Set<@Valid SubmittedAnswerFromStudentDTO> submittedAnswers) {

    public static QuizSubmissionFromStudentDTO of(QuizSubmission submission) {
        Set<SubmittedAnswerFromStudentDTO> submittedAnswerDTOs = submission.getSubmittedAnswers().stream().map(SubmittedAnswerFromStudentDTO::of).collect(Collectors.toSet());
        return new QuizSubmissionFromStudentDTO(submittedAnswerDTOs);
    }
}
