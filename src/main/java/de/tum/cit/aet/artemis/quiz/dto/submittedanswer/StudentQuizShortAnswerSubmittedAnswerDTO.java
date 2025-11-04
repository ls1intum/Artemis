package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizShortAnswerSubmittedAnswerDTO(@NotNull Long questionId, @NotNull Set<StudentQuizShortAnswerSubmittedTextDTO> submittedTexts)
        implements StudentQuizSubmittedAnswerDTO {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record StudentQuizShortAnswerSubmittedTextDTO(@NotEmpty @Size(max = MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH) String text, @NotNull Long spotId) {

}
