package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

/**
 * A ShortAnswerSolution.
 */
public class ShortAnswerSolution implements QuizQuestionComponent<ShortAnswerQuestion>, Serializable {

    private Long id;

    private String text;

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
