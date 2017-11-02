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
    private Set<PointCounter> ratedPointCounters = new HashSet<>();

    @OneToMany(mappedBy = "quizPointStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<PointCounter> unRatedPointCounters = new HashSet<>();

    @OneToOne(mappedBy = "quizPointStatistic")
    @JsonIgnore
    private QuizExercise quiz;


    public Set<PointCounter> getRatedPointCounters() {
        return ratedPointCounters;
    }

    public QuizPointStatistic ratedPointCounters(Set<PointCounter> pointCounters) {
        this.ratedPointCounters = pointCounters;
        return this;
    }

    public QuizPointStatistic addRatedPointCounter(PointCounter pointCounter) {
        this.ratedPointCounters.add(pointCounter);
        pointCounter.setQuizPointStatistic(this);
        return this;
    }

    public QuizPointStatistic removeRatedPointCounter(PointCounter pointCounter) {
        this.ratedPointCounters.remove(pointCounter);
        pointCounter.setQuizPointStatistic(null);
        return this;
    }

    public void setRatedPointCounters(Set<PointCounter> pointCounters) {
        this.ratedPointCounters = pointCounters;
    }

    public Set<PointCounter> getUnRatedPointCounters() {
        return unRatedPointCounters;
    }

    public QuizPointStatistic unRatedPointCounters(Set<PointCounter> pointCounters) {
        this.unRatedPointCounters = pointCounters;
        return this;
    }

    public QuizPointStatistic addUnRatedPointCounter(PointCounter pointCounter) {
        this.unRatedPointCounters.add(pointCounter);
        pointCounter.setQuizPointStatistic(this);
        return this;
    }

    public QuizPointStatistic removeUnRatedPointCounter(PointCounter pointCounter) {
        this.unRatedPointCounters.remove(pointCounter);
        pointCounter.setQuizPointStatistic(null);
        return this;
    }

    public void setUnRatedPointCounters(Set<PointCounter> pointCounters) {
        this.unRatedPointCounters = pointCounters;
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
