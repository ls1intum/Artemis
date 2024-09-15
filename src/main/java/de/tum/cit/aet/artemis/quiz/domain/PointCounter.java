package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A PointCounter.
 */
@Entity
@DiscriminatorValue(value = "PC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
