package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSpot.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpot extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion> {

    @JsonView(QuizView.Before.class)
    private Integer spotNr;

    @JsonView(QuizView.Before.class)
    private Integer width;

    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    public Integer getSpotNr() {
        return spotNr;
    }

    public ShortAnswerSpot spotNr(Integer spotNr) {
        this.spotNr = spotNr;
        return this;
    }

    public void setSpotNr(Integer spotNr) {
        this.spotNr = spotNr;
    }

    public Integer getWidth() {
        return width;
    }

    public ShortAnswerSpot width(Integer width) {
        this.width = width;
        return this;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public String toString() {
        return "ShortAnswerSpot{" + "id=" + getId() + ", width=" + getWidth() + ", spotNr=" + getSpotNr() + ", invalid='" + isInvalid() + "'" + "}";
    }

    // TODO: the following method should be removed
    @Override
    public void setQuestion(ShortAnswerQuestion quizQuestion) {

    }
}
