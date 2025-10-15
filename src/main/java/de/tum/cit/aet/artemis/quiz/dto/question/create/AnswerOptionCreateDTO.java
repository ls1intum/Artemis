package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionCreateDTO(@NotEmpty String text, String hint, String explanation, @NotNull Boolean isCorrect) {

    /**
     * Creates an {@link AnswerOptionCreateDTO} from the given {@link AnswerOption} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields,
     * including text, hint, explanation, and correctness flag.
     *
     * @param answerOption the {@link AnswerOption} domain object to convert
     * @return the {@link AnswerOptionCreateDTO} with properties set from the domain object
     */
    public static AnswerOptionCreateDTO of(AnswerOption answerOption) {
        return new AnswerOptionCreateDTO(answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect());
    }

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
