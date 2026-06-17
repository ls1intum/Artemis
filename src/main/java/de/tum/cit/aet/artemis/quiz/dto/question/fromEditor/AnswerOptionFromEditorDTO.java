package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

/**
 * DTO for answer options in the editor context.
 * Supports both creating new options (id is null) and updating existing options (id is non-null).
 *
 * @param id          the ID of the answer option, null for new options
 * @param text        the text of the answer option
 * @param hint        the hint for the answer option
 * @param explanation the explanation for the answer option
 * @param isCorrect   whether this option is correct
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionFromEditorDTO(Long id, @NotBlank @Size(max = 255) String text, @Size(max = 255) String hint, @Size(max = 500) String explanation,
        @NotNull Boolean isCorrect) {

    /**
     * Creates an AnswerOptionFromEditorDTO from the given AnswerOption domain object.
     *
     * @param answerOption the answer option to convert
     * @return the corresponding DTO
     */
    public static AnswerOptionFromEditorDTO of(AnswerOption answerOption) {
        return new AnswerOptionFromEditorDTO(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect());
    }

    /**
     * Creates a new AnswerOption domain object from this DTO.
     *
     * @return a new AnswerOption domain object
     */
    public AnswerOption toDomainObject() {
        AnswerOption answerOption = new AnswerOption();
        answerOption.setId(id);
        answerOption.setText(text);
        answerOption.setHint(hint);
        answerOption.setExplanation(explanation);
        answerOption.setIsCorrect(isCorrect);
        return answerOption;
    }

    /**
     * Applies the DTO values to an existing AnswerOption entity.
     *
     * @param answerOption the existing answer option to update
     */
    public void applyTo(AnswerOption answerOption) {
        answerOption.setText(text);
        answerOption.setHint(hint);
        answerOption.setExplanation(explanation);
        answerOption.setIsCorrect(isCorrect);
    }
}
