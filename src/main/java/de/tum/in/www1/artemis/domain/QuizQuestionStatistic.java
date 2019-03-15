package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.hibernate.annotations.Proxy;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import java.io.Serializable;
import java.util.Objects;

/**
 * A QuizQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value="Q")
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property="type")
@JsonSubTypes({
    @JsonSubTypes.Type(value=MultipleChoiceQuestionStatistic.class, name="multiple-choice"),
    @JsonSubTypes.Type(value=DragAndDropQuestionStatistic.class, name="drag-and-drop"),
    @JsonSubTypes.Type(value=ShortAnswerQuestionStatistic.class, name="short-answer")
})
@Proxy(lazy = false)
public abstract class QuizQuestionStatistic extends QuizStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "rated_correct_counter")
    private Integer ratedCorrectCounter = 0;

    @Column(name = "un_rated_correct_counter")
    private Integer unRatedCorrectCounter = 0;

    @OneToOne(mappedBy = "quizQuestionStatistic")
    @JsonIgnore
    private QuizQuestion quizQuestion;

    public Integer getRatedCorrectCounter() {
        return ratedCorrectCounter;
    }

    public QuizQuestionStatistic ratedCorrectCounter(Integer ratedCorrectCounter) {
        this.ratedCorrectCounter = ratedCorrectCounter;
        return this;
    }

    public void setRatedCorrectCounter(Integer ratedCorrectCounter) {
        this.ratedCorrectCounter = ratedCorrectCounter;
    }

    public Integer getUnRatedCorrectCounter() {
        return unRatedCorrectCounter;
    }

    public QuizQuestionStatistic unRatedCorrectCounter(Integer unRatedCorrectCounter) {
        this.unRatedCorrectCounter = unRatedCorrectCounter;
        return this;
    }

    public void setUnRatedCorrectCounter(Integer unRatedCorrectCounter) {
        this.unRatedCorrectCounter = unRatedCorrectCounter;
    }

    public QuizQuestion getQuizQuestion() {
        return quizQuestion;
    }

    public QuizQuestionStatistic question(QuizQuestion quizQuestion) {
        this.quizQuestion = quizQuestion;
        return this;
    }

    public void setQuizQuestion(QuizQuestion quizQuestion) {
        this.quizQuestion = quizQuestion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizQuestionStatistic quizQuestionStatistic = (QuizQuestionStatistic) o;
        if (quizQuestionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizQuestionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizQuestionStatistic{" +
            "id=" + getId() +
            ", ratedCorrectCounter='" + getRatedCorrectCounter() + "'" +
            ", unRatedCorrectCounter='" + getUnRatedCorrectCounter() + "'" +
            "}";
    }

    public abstract void addResult(SubmittedAnswer submittedAnswer, boolean rated);
    public abstract void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated);
    public abstract void resetStatistic();


}
