package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSolution.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSolution extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion> {

    @JsonView(QuizView.Before.class)
    private String text;

    @JsonView(QuizView.Before.class)
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

    // TODO: the following method should be removed
    @Override
    public void setQuestion(ShortAnswerQuestion quizQuestion) {

    }

    @Override
    public String toString() {
        return "ShortAnswerSolution{" + "id=" + getId() + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
