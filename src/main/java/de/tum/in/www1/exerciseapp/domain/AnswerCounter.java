package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A AnswerCounter.
 */
@Entity
@DiscriminatorValue(value="AC")
public class AnswerCounter extends StatisticCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @JsonIgnore
    private MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic;

    @OneToOne(cascade = {CascadeType.PERSIST})
    @JoinColumn(unique = true)
    private AnswerOption answer;


    public AnswerCounter multipleChoiceQuestionStatistic(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
        return this;
    }

    public MultipleChoiceQuestionStatistic getMultipleChoiceQuestionStatistic() {
        return multipleChoiceQuestionStatistic;
    }

    public void setMultipleChoiceQuestionStatistic(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
    }

    public AnswerOption getAnswer() {
        return answer;
    }

    public AnswerCounter answer(AnswerOption answerOption) {
        this.answer = answerOption;
        return this;
    }

    public void setAnswer(AnswerOption answerOption) {
        this.answer = answerOption;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnswerCounter answerCounter = (AnswerCounter) o;
        if (answerCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), answerCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "AnswerCounter{" +
            "id=" + getId() +
            "}";
    }
}
