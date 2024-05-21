package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A PointCounter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PointCounter extends TempIdObject implements Serializable {

    @JsonIgnore
    private QuizPointStatistic quizPointStatistic;

    private Double points;

    private Integer ratedCounter = 0;

    private Integer unRatedCounter = 0;

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

    @Override
    public String toString() {
        return "PointCounter{" + "id=" + getId() + ", points='" + getPoints() + "'" + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
