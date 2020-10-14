package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A PointCounter.
 */
@Entity
@DiscriminatorValue(value = "PC")
public class PointCounter extends QuizStatisticCounter {

    @Column(name = "points")
    private Double points;

    @ManyToOne
    @JsonIgnore
    private QuizPointStatistic quizPointStatistic;

    public Double getPoints() {
        return points;
    }

    public void setPoints(Double points) {
        this.points = points;
    }

    public QuizPointStatistic getQuizPointStatistic() {
        return quizPointStatistic;
    }

    public void setQuizPointStatistic(QuizPointStatistic quizPointStatistic) {
        this.quizPointStatistic = quizPointStatistic;
    }

    @Override
    public String toString() {
        return "PointCounter{" + "id=" + getId() + ", points='" + getPoints() + "'" + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
