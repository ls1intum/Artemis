package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A AnswerOption.
 */
public class AnswerOption extends TempIdObject implements QuizQuestionComponent<MultipleChoiceQuestion>, Serializable {

    private String text;

    private String hint;

    private String explanation;

    private Boolean isCorrect;

    private Boolean invalid = false;

    @Override
    public void setQuestion(MultipleChoiceQuestion quizQuestion) {

    }

    public String getText() {
        return text;
    }

    public AnswerOption text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHint() {
        return hint;
    }

    public AnswerOption hint(String hint) {
        this.hint = hint;
        return this;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public AnswerOption explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public AnswerOption isInvalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Boolean isIsCorrect() {
        return isCorrect;
    }

    public AnswerOption isCorrect(Boolean isCorrect) {
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

    @Override
    public String toString() {
        return "AnswerOptionDTO{" + "id=" + getId() + ", text='" + getText() + "'" + ", hint='" + "'" + ", explanation='" + "'" + ", isCorrect='" + isIsCorrect() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }
}
