package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A ShortAnswerSpotCounter.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpotCounter extends QuizStatisticCounter implements QuizQuestionStatisticComponent<ShortAnswerQuestionStatistic, ShortAnswerSpot, ShortAnswerQuestion> {

    @ManyToOne
    @JsonIgnore
    private ShortAnswerQuestionStatistic shortAnswerQuestionStatistic;

    @OneToOne(cascade = { CascadeType.PERSIST })
    @JoinColumn(unique = true)
    private ShortAnswerSpot spot;

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
        setShortAnswerQuestionStatistic(shortAnswerQuestionStatistic);
    }

    @Override
    @JsonIgnore
    public ShortAnswerSpot getQuizQuestionComponent() {
        return getSpot();
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionComponent(ShortAnswerSpot shortAnswerSpot) {
        setSpot(shortAnswerSpot);
    }

    @Override
    public String toString() {
        return "ShortAnswerSpotCounter{" + "id=" + getId() + "}";
    }
}
