package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedTextFromStudentDTO(@NotBlank String text, @NotNull Long spotId) {
}
