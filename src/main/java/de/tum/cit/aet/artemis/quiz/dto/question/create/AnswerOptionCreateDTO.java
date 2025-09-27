package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionCreateDTO(@NotEmpty String text, String hint, String explanation, boolean isCorrect) {

    /**
     * Converts this DTO to an {@link AnswerOption} domain object.
     * <p>
     * Maps the DTO properties directly to the corresponding fields in the domain object,
     * including text, hint, explanation, and correctness flag.
     *
     * @return the {@link AnswerOption} domain object with properties set from this DTO
     */
    public AnswerOption toDomainObject() {
        AnswerOption answerOption = new AnswerOption();
        answerOption.setText(text);
        answerOption.setHint(hint);
        answerOption.setExplanation(explanation);
        answerOption.setIsCorrect(isCorrect);
        return answerOption;
    }
}
