package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

/**
 * A AnswerOption.
 */
public class AnswerOptionDTO implements Serializable {

    private Long id;

    private String text;

    private String hint;

    private String explanation;

    private Boolean isCorrect;

    private Boolean invalid = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public AnswerOptionDTO text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHint() {
        return hint;
    }

    public AnswerOptionDTO hint(String hint) {
        this.hint = hint;
        return this;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public AnswerOptionDTO explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public AnswerOptionDTO isInvalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Boolean isIsCorrect() {
        return isCorrect;
    }

    public AnswerOptionDTO isCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
        return this;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public static AnswerOptionDTO convertToAnswerOptionDTO(AnswerOption answerOption) {
        AnswerOptionDTO answerOptionDTO = new AnswerOptionDTO();
        answerOptionDTO.setHint(answerOption.getHint());
        answerOptionDTO.setExplanation(answerOption.getExplanation());
        answerOptionDTO.setInvalid(answerOption.isInvalid());
        answerOptionDTO.setText(answerOption.getText());
        answerOptionDTO.setIsCorrect(answerOption.isIsCorrect());
        return answerOptionDTO;
    }

    @Override
    public String toString() {
        return "AnswerOptionDTO{" + "id=" + getId() + ", text='" + getText() + "'" + ", hint='" + "'" + ", explanation='" + "'" + ", isCorrect='" + isIsCorrect() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }

}
