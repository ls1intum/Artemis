package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A AnswerOption.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerOption extends TempIdObject implements QuizQuestionComponent<MultipleChoiceQuestion>, Serializable {

    @JsonView(QuizView.Before.class)
    private String text;

    @JsonView(QuizView.Before.class)
    private String hint;

    @JsonView(QuizView.After.class)
    private String explanation;

    @JsonView(QuizView.After.class)
    private Boolean isCorrect;

    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    // TODO: the following method should be removed
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

    /**
     * Checks if the current object's explanation is valid.
     *
     * @return true if the explanation length is less than or equal to 500 characters, false otherwise.
     */
    public boolean isValid() {
        String explanation = this.getExplanation();
        return explanation == null || explanation.length() <= 500;
    }

    @Override
    public String toString() {
        return "AnswerOption{" + "id=" + getId() + ", text='" + getText() + "'" + ", hint='" + getHint() + "'" + ", explanation='" + getExplanation() + "'" + ", isCorrect='"
                + isIsCorrect() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
