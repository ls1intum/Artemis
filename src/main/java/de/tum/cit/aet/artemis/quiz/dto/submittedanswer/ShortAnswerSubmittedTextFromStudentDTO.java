package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// The submitted-text length limit (formerly enforced by the short_answer_submitted_text.text varchar(255) column) is enforced here, matching the live-client submission DTO, since
// the submitted text is now stored in the submitted_answer.selection JSON column without a per-field length constraint.
public record ShortAnswerSubmittedTextFromStudentDTO(@NotBlank @Size(max = MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH, message = "The submitted answer text is too long.") String text,
        @NotNull Long spotId) {

    public static ShortAnswerSubmittedTextFromStudentDTO of(ShortAnswerSubmittedText submittedText) {
        return new ShortAnswerSubmittedTextFromStudentDTO(submittedText.getText(), submittedText.getSpot().getId());
    }
}
