package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_EXPLANATION_LENGTH;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

// The explanation length limit (formerly enforced by the answer_option.explanation varchar(500) column) is enforced here too, matching AnswerOptionCreateDTO, since the answer
// option is stored in the quiz_question.content JSON column without a per-field length constraint.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionReEvaluateDTO(@NotNull Long id, @NotNull String text, String hint, @Size(max = MAX_QUIZ_EXPLANATION_LENGTH) String explanation,
        @NotNull Boolean isCorrect, @NotNull Boolean invalid) {

    public static AnswerOptionReEvaluateDTO of(AnswerOption answerOption) {
        return new AnswerOptionReEvaluateDTO(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect(),
                answerOption.isInvalid());
    }
}
