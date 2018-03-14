package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.io.Serializable;
import java.util.Objects;

/**
 * A PointCounter.
 */
@Entity
@DiscriminatorValue(value="PC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class PointCounter extends StatisticCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "points")
    private Double points;

    @ManyToOne
    @JsonIgnore
    private QuizPointStatistic quizPointStatistic;

    public Double getPoints() {
        return points;
    }

    public PointCounter points(Double points) {
        this.points = points;
        return this;
    }

    public void setPoints(Double points) {
        this.points = points;
    }

    public QuizPointStatistic getQuizPointStatistic() {
        return quizPointStatistic;
    }

    public PointCounter quizPointStatistic(QuizPointStatistic quizPointStatistic) {
        this.quizPointStatistic = quizPointStatistic;
        return this;
    }

    public void setQuizPointStatistic(QuizPointStatistic quizPointStatistic) {
        this.quizPointStatistic = quizPointStatistic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PointCounter pointCounter = (PointCounter) o;
        if (pointCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), pointCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "PointCounter{" +
            "id=" + getId() +
            ", points='" + getPoints() + "'" +
            "}";
    }
}
