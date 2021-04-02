package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.*;

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
