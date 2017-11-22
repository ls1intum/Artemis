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
@JsonTypeName("multiple-choice")
public class MultipleChoiceQuestionStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;


    @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true, mappedBy = "multipleChoiceQuestionStatistic")
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
    //creates the AnswerCounter for the new AnswerOption
    //      if where is already an AnswerCounter with the given answerOption -> nothing happens
    public void addAnswerOption(AnswerOption answer){

        if(answer ==null){
            return;
        }

        for(AnswerCounter counter: answerCounters){
            if(answer.equals(counter.getAnswer())){
                return;
            }
        }
        AnswerCounter answerCounter = new AnswerCounter();
        answerCounter.setAnswer(answer);
        addAnswerCounters(answerCounter);

    }

    @Override
    //adds new Result to the MultipleChoiceQuestionStatistic
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated){

        if(submittedAnswer == null){
            return;
        }

        MultipleChoiceSubmittedAnswer mcSubmittedAnswer = (MultipleChoiceSubmittedAnswer)submittedAnswer;

        if(rated){
            setParticipantsRated(getParticipantsRated()+1);

            if(mcSubmittedAnswer.getSelectedOptions() == null){
                return;
            }

            for (AnswerCounter answerCounter: answerCounters){
                if(mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())){
                        answerCounter.setRatedCounter(answerCounter.getRatedCounter()+1);
                }
            }
            if(getQuestion().isAnswerCorrect(mcSubmittedAnswer)){
                setRatedCorrectCounter(getRatedCorrectCounter()+1);
            }
        }
        else{
            setParticipantsUnrated(getParticipantsUnrated()+1);

            if(mcSubmittedAnswer.getSelectedOptions() == null){
                return;
            }
            for (AnswerCounter answerCounter: answerCounters){
                if(mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())){
                    answerCounter.setUnRatedCounter(answerCounter.getUnRatedCounter()+1);
                }
            }
            if(getQuestion().isAnswerCorrect(mcSubmittedAnswer)){
                setUnRatedCorrectCounter(getUnRatedCorrectCounter()+1);
            }
        }
    }


}
