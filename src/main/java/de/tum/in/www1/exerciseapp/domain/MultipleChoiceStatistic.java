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
 * A MultipleChoiceStatistic.
 */
@Entity
@DiscriminatorValue(value="MC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("multiple-choice")
public class MultipleChoiceStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true, mappedBy = "multipleChoiceStatistic")
    //@JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<AnswerCounter> ratedAnswerCounters = new HashSet<>();

    @OneToMany(cascade=CascadeType.ALL, mappedBy = "multipleChoiceStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<AnswerCounter> unRatedAnswerCounters = new HashSet<>();

    public Set<AnswerCounter> getRatedAnswerCounters() {
        return ratedAnswerCounters;
    }

    public MultipleChoiceStatistic ratedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.ratedAnswerCounters = answerCounters;
        return this;
    }

    public MultipleChoiceStatistic addRatedAnswerCounter(AnswerCounter answerCounter) {
        this.ratedAnswerCounters.add(answerCounter);
        answerCounter.setMultipleChoiceStatistic(this);
        return this;
    }

    public MultipleChoiceStatistic removeRatedAnswerCounter(AnswerCounter answerCounter) {
        this.ratedAnswerCounters.remove(answerCounter);
        answerCounter.setMultipleChoiceStatistic(null);
        return this;
    }

    public void setRatedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.ratedAnswerCounters = answerCounters;
    }

    public Set<AnswerCounter> getUnRatedAnswerCounters() {
        return unRatedAnswerCounters;
    }

    public MultipleChoiceStatistic unRatedAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.unRatedAnswerCounters = answerCounters;
        return this;
    }

    public MultipleChoiceStatistic addUnRatedAnswerCounter(AnswerCounter answerCounter) {
        this.unRatedAnswerCounters.add(answerCounter);
        answerCounter.setMultipleChoiceStatistic(this);
        return this;
    }

    public MultipleChoiceStatistic removeUnRatedAnswerCounter(AnswerCounter answerCounter) {
        this.unRatedAnswerCounters.remove(answerCounter);
        answerCounter.setMultipleChoiceStatistic(null);
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
        MultipleChoiceStatistic multipleChoiceStatistic = (MultipleChoiceStatistic) o;
        if (multipleChoiceStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), multipleChoiceStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MultipleChoiceStatistic{" +
            "id=" + getId() +
            "}";
    }

    public void addAnswerOption(AnswerOption answer){
        AnswerCounter ratedCounter = new AnswerCounter();
        ratedCounter.setAnswer(answer);
        addRatedAnswerCounter(ratedCounter);

        // TO-DO solve Problem with same IDs for different CounterSets
        //AnswerCounter unRatedCounter = new AnswerCounter();
        //unRatedCounter.setAnswer(answer);
        //addUnRatedAnswerCounter(unRatedCounter);

    }

}
