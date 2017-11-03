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
    private Set<AnswerCounter> ratedAnswerCounters = new HashSet<>();

    @OneToMany(cascade=CascadeType.ALL, mappedBy = "multipleChoiceQuestionStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<AnswerCounter> unRatedAnswerCounters = new HashSet<>();

    public Set<AnswerCounter> getRatedAnswerCounters() {
        return ratedAnswerCounters;
    }

    public MultipleChoiceQuestionStatistic ratedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.ratedAnswerCounters = answerCounters;
        return this;
    }

    public MultipleChoiceQuestionStatistic addRatedAnswerCounter(AnswerCounter answerCounter) {
        this.ratedAnswerCounters.add(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(this);
        return this;
    }

    public MultipleChoiceQuestionStatistic removeRatedAnswerCounter(AnswerCounter answerCounter) {
        this.ratedAnswerCounters.remove(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(null);
        return this;
    }

    public void setRatedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.ratedAnswerCounters = answerCounters;
    }

    public Set<AnswerCounter> getUnRatedAnswerCounters() {
        return unRatedAnswerCounters;
    }

    public MultipleChoiceQuestionStatistic unRatedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.unRatedAnswerCounters = answerCounters;
        return this;
    }

    public MultipleChoiceQuestionStatistic addUnRatedAnswerCounter(AnswerCounter answerCounter) {
        this.unRatedAnswerCounters.add(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(this);
        return this;
    }

    public MultipleChoiceQuestionStatistic removeUnRatedAnswerCounter(AnswerCounter answerCounter) {
        this.unRatedAnswerCounters.remove(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(null);
        return this;
    }

    public void setUnRatedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.unRatedAnswerCounters = answerCounters;
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
