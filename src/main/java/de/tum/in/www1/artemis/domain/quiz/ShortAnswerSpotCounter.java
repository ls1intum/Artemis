package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A ShortAnswerSpotCounter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpotCounter extends TempIdObject implements QuizQuestionStatisticComponent<ShortAnswerQuestionStatistic, ShortAnswerSpot, ShortAnswerQuestion> {

    @JsonIgnore
    private ShortAnswerQuestionStatistic shortAnswerQuestionStatistic;

    private ShortAnswerSpot spot;

    private Integer ratedCounter = 0;

    private Integer unRatedCounter = 0;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    public ShortAnswerQuestionStatistic getShortAnswerQuestionStatistic() {
        return shortAnswerQuestionStatistic;
    }

    public void setShortAnswerQuestionStatistic(ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {
        this.shortAnswerQuestionStatistic = shortAnswerQuestionStatistic;
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionStatistic(ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {

    }

    @Override
    @JsonIgnore
    public ShortAnswerSpot getQuizQuestionComponent() {
        return null;
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionComponent(ShortAnswerSpot shortAnswerSpot) {

    }

    @Override
    public String toString() {
        return "ShortAnswerSpotCounter{" + "id=" + getId() + "}";
    }

    public Integer getRatedCounter() {
        return ratedCounter;
    }

    public void setRatedCounter(Integer ratedCounter) {
        this.ratedCounter = ratedCounter;
    }

    public Integer getUnRatedCounter() {
        return unRatedCounter;
    }

    public void setUnRatedCounter(Integer unRatedCounter) {
        this.unRatedCounter = unRatedCounter;
    }
}
