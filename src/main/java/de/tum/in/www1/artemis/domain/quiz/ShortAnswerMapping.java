package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A ShortAnswerMapping.
 */
public class ShortAnswerMapping extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion>, Serializable {

    private Integer shortAnswerSpotIndex;

    private Integer shortAnswerSolutionIndex;

    private Boolean invalid;

    private ShortAnswerSolution solution;

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

    @Override
    public void setQuestion(ShortAnswerQuestion quizQuestion) {

    }
}
