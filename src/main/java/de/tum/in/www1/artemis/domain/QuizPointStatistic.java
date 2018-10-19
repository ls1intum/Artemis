package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A QuizPointStatistic.
 */
@Entity
@Table(name = "quiz_point_statistic")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizPointStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "quizPointStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<PointCounter> pointCounters = new HashSet<>();
    @OneToOne(mappedBy = "quizPointStatistic")
    @JsonIgnore
    private QuizExercise quiz;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
