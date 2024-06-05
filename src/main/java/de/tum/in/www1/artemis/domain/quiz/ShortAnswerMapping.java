package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerMapping.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerMapping extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion>, Serializable {

    @JsonView(QuizView.Before.class)
    private Integer shortAnswerSpotIndex;

    @JsonView(QuizView.Before.class)
    private Integer shortAnswerSolutionIndex;

    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    @JsonView(QuizView.Before.class)
    private ShortAnswerSolution solution;

    @JsonView(QuizView.Before.class)
    private ShortAnswerSpot spot;

    public Integer getShortAnswerSpotIndex() {
        return shortAnswerSpotIndex;
    }

    public void setShortAnswerSpotIndex(Integer shortAnswerSpotIndex) {
        this.shortAnswerSpotIndex = shortAnswerSpotIndex;
    }

    public Integer getShortAnswerSolutionIndex() {
        return shortAnswerSolutionIndex;
    }

    public void setShortAnswerSolutionIndex(Integer shortAnswerSolutionIndex) {
        this.shortAnswerSolutionIndex = shortAnswerSolutionIndex;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerSolution getSolution() {
        return solution;
    }

    public ShortAnswerMapping solution(ShortAnswerSolution shortAnswerSolution) {
        this.solution = shortAnswerSolution;
        return this;
    }

    public void setSolution(ShortAnswerSolution shortAnswerSolution) {
        this.solution = shortAnswerSolution;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public ShortAnswerMapping spot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
        return this;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    @Override
    public String toString() {
        return "ShortAnswerMapping{" + "id=" + getId() + ", shortAnswerSpotIndex=" + getShortAnswerSpotIndex() + ", shortAnswerSolutionIndex=" + getShortAnswerSolutionIndex()
                + ", invalid='" + isInvalid() + "'" + "}";
    }

    // TODO: the following method should be removed
    @Override
    public void setQuestion(ShortAnswerQuestion quizQuestion) {

    }
}
