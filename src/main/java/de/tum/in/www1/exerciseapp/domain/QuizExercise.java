package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A QuizExercise.
 */
@Entity
@DiscriminatorValue(value="Q")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizExercise extends Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "is_visible_before_start")
    private Boolean isVisibleBeforeStart;

    @Column(name = "is_open_for_practice")
    private Boolean isOpenForPractice;

    @Column(name = "is_planned_to_start")
    private Boolean isPlannedToStart;

    @Column(name = "duration")
    private Integer duration;

    @OneToMany(mappedBy = "exercise")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Question> questions = new HashSet<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    public Boolean isIsVisibleBeforeStart() {
        return isVisibleBeforeStart;
    }

    public QuizExercise isVisibleBeforeStart(Boolean isVisibleBeforeStart) {
        this.isVisibleBeforeStart = isVisibleBeforeStart;
        return this;
    }

    public void setIsVisibleBeforeStart(Boolean isVisibleBeforeStart) {
        this.isVisibleBeforeStart = isVisibleBeforeStart;
    }

    public Boolean isIsOpenForPractice() {
        return isOpenForPractice;
    }

    public QuizExercise isOpenForPractice(Boolean isOpenForPractice) {
        this.isOpenForPractice = isOpenForPractice;
        return this;
    }

    public void setIsOpenForPractice(Boolean isOpenForPractice) {
        this.isOpenForPractice = isOpenForPractice;
    }

    public Boolean isIsPlannedToStart() {
        return isPlannedToStart;
    }

    public QuizExercise isPlannedToStart(Boolean isPlannedToStart) {
        this.isPlannedToStart = isPlannedToStart;
        return this;
    }

    public void setIsPlannedToStart(Boolean isPlannedToStart) {
        this.isPlannedToStart = isPlannedToStart;
    }

    public Integer getDuration() {
        return duration;
    }

    public QuizExercise duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Set<Question> getQuestions() {
        return questions;
    }

    public QuizExercise questions(Set<Question> questions) {
        this.questions = questions;
        return this;
    }

    public QuizExercise addQuestions(Question question) {
        this.questions.add(question);
        question.setExercise(this);
        return this;
    }

    public QuizExercise removeQuestions(Question question) {
        this.questions.remove(question);
        question.setExercise(null);
        return this;
    }

    public void setQuestions(Set<Question> questions) {
        this.questions = questions;
    }
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizExercise quizExercise = (QuizExercise) o;
        if (quizExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizExercise{" +
            "id=" + getId() +
            ", isVisibleBeforeStart='" + isIsVisibleBeforeStart() + "'" +
            ", isOpenForPractice='" + isIsOpenForPractice() + "'" +
            ", isPlannedToStart='" + isIsPlannedToStart() + "'" +
            ", duration='" + getDuration() + "'" +
            "}";
    }
}
