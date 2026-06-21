package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_EXPLANATION_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_HINT_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_TEXT_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionReEvaluateDTO(@NotNull Long id, @NotBlank @Size(max = MAX_QUIZ_ANSWER_OPTION_TEXT_LENGTH) String text,
        @Size(max = MAX_QUIZ_ANSWER_OPTION_HINT_LENGTH) String hint, @Size(max = MAX_QUIZ_ANSWER_OPTION_EXPLANATION_LENGTH) String explanation, @NotNull Boolean isCorrect,
        @NotNull Boolean invalid) {

    public static AnswerOptionReEvaluateDTO of(AnswerOption answerOption) {
        return new AnswerOptionReEvaluateDTO(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect(),
                answerOption.isInvalid());
    }
}
