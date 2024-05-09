package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

/**
 * A ShortAnswerSolution.
 */
public class ShortAnswerSolution implements QuizQuestionComponent<ShortAnswerQuestion>, Serializable {

    private Long id = 1L;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ShortAnswerSolution shortAnswerSolution = (ShortAnswerSolution) obj;
        if (shortAnswerSolution.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerSolution.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
