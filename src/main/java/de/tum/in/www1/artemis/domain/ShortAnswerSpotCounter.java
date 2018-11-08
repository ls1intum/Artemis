package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ShortAnswerSpotCounter.
 */
@Entity
@DiscriminatorValue(value="SA")
public class ShortAnswerSpotCounter extends StatisticCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @JsonIgnore
    private ShortAnswerQuestionStatistic shortAnswerQuestionStatistic;

    @OneToOne(cascade = {CascadeType.PERSIST})
    @JoinColumn(unique = true)
    private ShortAnswerSpot spot;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public ShortAnswerSpotCounter spot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
        return this;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    public ShortAnswerQuestionStatistic getShortAnswerQuestionStatistic() {
        return shortAnswerQuestionStatistic;
    }

    public ShortAnswerSpotCounter shortAnswerQuestionStatistic(ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {
        this.shortAnswerQuestionStatistic = shortAnswerQuestionStatistic;
        return this;
    }

    public void setShortAnswerQuestionStatistic(ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {
        this.shortAnswerQuestionStatistic = shortAnswerQuestionStatistic;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShortAnswerSpotCounter shortAnswerSpotCounter = (ShortAnswerSpotCounter) o;
        if (shortAnswerSpotCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerSpotCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerSpotCounter{" +
            "id=" + getId() +
            "}";
    }
}
