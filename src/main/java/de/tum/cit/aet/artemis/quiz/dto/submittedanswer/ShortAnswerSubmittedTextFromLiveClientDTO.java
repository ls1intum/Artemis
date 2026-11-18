package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShortAnswerSubmittedTextFromLiveClientDTO(@Size(max = MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH, message = "The submitted answer text is too long.") String text,
        EntityIdRefDTO spot) {
}
