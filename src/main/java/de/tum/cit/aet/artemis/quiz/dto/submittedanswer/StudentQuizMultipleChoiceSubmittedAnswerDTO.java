package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizMultipleChoiceSubmittedAnswerDTO(@NotEmpty Long questionId, @NotEmpty Set<@NotNull Long> selectedOptionIds) implements StudentQuizSubmittedAnswerDTO {
}
