package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A MultipleChoiceQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value="MC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("multiple-choice")
public class MultipleChoiceQuestionStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;


    @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true, mappedBy = "multipleChoiceQuestionStatistic")
    //@JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<AnswerCounter> answerCounters = new HashSet<>();


    public Set<AnswerCounter> getAnswerCounters() {
        return answerCounters;
    }

    public MultipleChoiceQuestionStatistic answerCounters(Set<AnswerCounter> answerCounters) {
        this.answerCounters = answerCounters;
        return this;
    }

    public MultipleChoiceQuestionStatistic addAnswerCounters(AnswerCounter answerCounter) {
        this.answerCounters.add(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(this);
        return this;
    }

    public MultipleChoiceQuestionStatistic removeAnswerCounters(AnswerCounter answerCounter) {
        this.answerCounters.remove(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(null);
        return this;
    }

    public void setAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.answerCounters = answerCounters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = (MultipleChoiceQuestionStatistic) o;
        if (multipleChoiceQuestionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), multipleChoiceQuestionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MultipleChoiceQuestionStatistic{" +
            "id=" + getId() +
            "}";
    }
    public void addAnswerOption(AnswerOption answer){

        AnswerCounter answerCounter = new AnswerCounter();
        answerCounter.setAnswer(answer);
        addAnswerCounters(answerCounter);

    }
}
