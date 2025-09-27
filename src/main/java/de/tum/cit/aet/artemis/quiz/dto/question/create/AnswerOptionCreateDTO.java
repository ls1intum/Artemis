package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionCreateDTO(@NotEmpty String text, String hint, String explanation, boolean isCorrect) {

    public AnswerOption toDomainObject() {
        AnswerOption answerOption = new AnswerOption();
        answerOption.setText(text);
        answerOption.setHint(hint);
        answerOption.setExplanation(explanation);
        answerOption.setIsCorrect(isCorrect);
        return answerOption;
    }
}
