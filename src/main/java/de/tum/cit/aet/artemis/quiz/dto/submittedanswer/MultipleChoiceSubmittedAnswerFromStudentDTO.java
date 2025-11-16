package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceSubmittedAnswerFromStudentDTO(@NotNull Long questionId, @NotNull Set<Long> selectedOptions) implements SubmittedAnswerFromStudentDTO {
}
