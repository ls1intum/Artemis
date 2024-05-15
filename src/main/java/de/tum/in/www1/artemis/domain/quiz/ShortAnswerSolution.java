package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A ShortAnswerSolution.
 */
public class ShortAnswerSolution extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion>, Serializable {

    private String text;

    private Boolean invalid = false;

    public String getText() {
        return text;
    }

    public ShortAnswerSolution text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public void setQuestion(ShortAnswerQuestion quizQuestion) {

    }

    @Override
    public String toString() {
        return "ShortAnswerSolution{" + "id=" + getId() + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
