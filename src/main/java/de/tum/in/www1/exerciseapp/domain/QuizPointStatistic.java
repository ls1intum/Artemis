package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * A QuizPointStatistic.
 */
@Entity
@DiscriminatorValue(value="QP")
//@Table(name = "quiz_point_statistic")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizPointStatistic extends Statistic implements Serializable {

    private static final long serialVersionUID = 1L;


    @OneToMany(mappedBy = "quizPointStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<PointCounter> pointCounters = new HashSet<>();

    @OneToOne(mappedBy = "quizPointStatistic")
    @JsonIgnore
    private QuizExercise quiz;

    public Set<PointCounter> getPointCounters() {
        return pointCounters;
    }

    public QuizPointStatistic pointCounters(Set<PointCounter> pointCounters) {
        this.pointCounters = pointCounters;
        return this;
    }

    public QuizPointStatistic addPointCounters(PointCounter pointCounter) {
        this.pointCounters.add(pointCounter);
        pointCounter.setQuizPointStatistic(this);
        return this;
    }

    public QuizPointStatistic removePointCounters(PointCounter pointCounter) {
        this.pointCounters.remove(pointCounter);
        pointCounter.setQuizPointStatistic(null);
        return this;
    }

    public void setPointCounters(Set<PointCounter> pointCounters) {
        this.pointCounters = pointCounters;
    }

    public QuizExercise getQuiz() {
        return quiz;
    }

    public QuizPointStatistic quiz(QuizExercise quizExercise) {
        this.quiz = quizExercise;
        return this;
    }

    public void setQuiz(QuizExercise quizExercise) {
        this.quiz = quizExercise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizPointStatistic quizPointStatistic = (QuizPointStatistic) o;
        if (quizPointStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizPointStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizPointStatistic{" +
            "id=" + getId() +
            "}";
    }
}
