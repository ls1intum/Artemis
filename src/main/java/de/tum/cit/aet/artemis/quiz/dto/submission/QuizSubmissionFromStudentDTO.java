package de.tum.cit.aet.artemis.quiz.dto.submission;

import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromStudentDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmissionFromStudentDTO(@NotNull Set<@Valid SubmittedAnswerFromStudentDTO> submittedAnswers) {
}
