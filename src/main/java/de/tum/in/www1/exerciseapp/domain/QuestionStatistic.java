package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A QuestionStatistic.
 */
@Entity
@DiscriminatorValue(value="Q")
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property="type")

@JsonSubTypes({
    @JsonSubTypes.Type(value=MultipleChoiceQuestionStatistic.class, name="multiple-choice"),
    @JsonSubTypes.Type(value=DragAndDropQuestionStatistic.class, name="drag-and-drop")
})
public abstract class QuestionStatistic extends Statistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "rated_correct_counter")
    private Integer ratedCorrectCounter = 0;

    @Column(name = "un_rated_correct_counter")
    private Integer unRatedCorrectCounter = 0;

    @OneToOne(mappedBy = "questionStatistic")
    @JsonIgnore
    private Question question;

    public Integer getRatedCorrectCounter() {
        return ratedCorrectCounter;
    }

    public QuestionStatistic ratedCorrectCounter(Integer ratedCorrectCounter) {
        this.ratedCorrectCounter = ratedCorrectCounter;
        return this;
    }

    public void setRatedCorrectCounter(Integer ratedCorrectCounter) {
        this.ratedCorrectCounter = ratedCorrectCounter;
    }

    public Integer getUnRatedCorrectCounter() {
        return unRatedCorrectCounter;
    }

    public QuestionStatistic unRatedCorrectCounter(Integer unRatedCorrectCounter) {
        this.unRatedCorrectCounter = unRatedCorrectCounter;
        return this;
    }

    public void setUnRatedCorrectCounter(Integer unRatedCorrectCounter) {
        this.unRatedCorrectCounter = unRatedCorrectCounter;
    }

    public Question getQuestion() {
        return question;
    }

    public QuestionStatistic question(Question question) {
        this.question = question;
        return this;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuestionStatistic questionStatistic = (QuestionStatistic) o;
        if (questionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), questionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuestionStatistic{" +
            "id=" + getId() +
            ", ratedCorrectCounter='" + getRatedCorrectCounter() + "'" +
            ", unRatedCorrectCounter='" + getUnRatedCorrectCounter() + "'" +
            "}";
    }

    public abstract void addResult(SubmittedAnswer submittedAnswer, boolean rated);



}
