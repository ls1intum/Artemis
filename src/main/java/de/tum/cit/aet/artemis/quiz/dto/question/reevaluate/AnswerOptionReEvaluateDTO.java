package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionReEvaluateDTO(@NotNull Long id, @NotBlank @Size(max = 255) String text, @Size(max = 255) String hint, @Size(max = 500) String explanation,
        @NotNull Boolean isCorrect, @NotNull Boolean invalid) {

    public static AnswerOptionReEvaluateDTO of(AnswerOption answerOption) {
        return new AnswerOptionReEvaluateDTO(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect(),
                answerOption.isInvalid());
    }
}
