package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedTextFromStudentDTO(@NotBlank String text, @NotNull Long spotId) {

    public static ShortAnswerSubmittedTextFromStudentDTO of(ShortAnswerSubmittedText submittedText) {
        return new ShortAnswerSubmittedTextFromStudentDTO(submittedText.getText(), submittedText.getSpot().getId());
    }
}
