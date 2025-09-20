package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionCreateDTO(@NotEmpty String text, String hint, String explanation, boolean isCorrect) {
}
