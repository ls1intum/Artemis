package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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

    /**
     * 1. creates the AnswerCounter for the new AnswerOption
     *          if where is already an AnswerCounter with the given answerOption -> nothing happens
     *
     * @param answer the answer object which will be added to the MultipleChoiceStatistic
     */
    public void addAnswerOption(AnswerOption answer) {

        if(answer ==null) {
            return;
        }

        for(AnswerCounter counter: answerCounters) {
            if(answer.equals(counter.getAnswer())) {
                return;
            }
        }
        AnswerCounter answerCounter = new AnswerCounter();
        answerCounter.setAnswer(answer);
        addAnswerCounters(answerCounter);

    }

    /**
     * 1. check if the Result is rated or unrated
     * 2. increase participants, all selected AnswerCounter and if the question is correct, than increase the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise)
     *                                  or unrated  ( participated after the dueDate of the quizExercise)
     */
    @Override
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated) {

        if(submittedAnswer == null) {
            return;
        }

        MultipleChoiceSubmittedAnswer mcSubmittedAnswer = (MultipleChoiceSubmittedAnswer)submittedAnswer;

        if(rated) {
            //increase the rated participants
            setParticipantsRated(getParticipantsRated()+1);

            if(mcSubmittedAnswer.getSelectedOptions() != null) {
                // increase rated answerCounter if answer is selected
                for (AnswerCounter answerCounter: answerCounters) {
                    if(mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())) {
                        answerCounter.setRatedCounter(answerCounter.getRatedCounter()+1);
                    }
                }
            }
            // increase rated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(mcSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter()+1);
            }
        }
        // Result is unrated
        else{
            //increase the unrated participants
            setParticipantsUnrated(getParticipantsUnrated()+1);

            if(mcSubmittedAnswer.getSelectedOptions() != null) {
                for (AnswerCounter answerCounter: answerCounters) {
                    // increase unrated answerCounter if answer is selected
                    if(mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())) {
                        answerCounter.setUnRatedCounter(answerCounter.getUnRatedCounter()+1);
                    }
                }
            }

            // increase unrated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(mcSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter()+1);
            }
        }
    }

    /**
     * 1. check if the Result is rated or unrated
     * 2. decrease participants, all selected AnswerCounter and if the question is correct, than decrease the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise)
     *                                  or unrated  ( participated after the dueDate of the quizExercise)
     */
    @Override
    public void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated) {

        if(submittedAnswer == null) {
            return;
        }

        MultipleChoiceSubmittedAnswer mcSubmittedAnswer = (MultipleChoiceSubmittedAnswer)submittedAnswer;

        if(rated) {
            //decrease rated participants
            setParticipantsRated(getParticipantsRated()-1);

            if(mcSubmittedAnswer.getSelectedOptions() != null) {
                // decrease rated answerCounter if answer is selected
                for (AnswerCounter answerCounter: answerCounters) {
                    if(mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())) {
                        answerCounter.setRatedCounter(answerCounter.getRatedCounter()-1);
                    }
                }
            }
            // decrease rated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(mcSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter()-1);
            }
        }
        // Result is unrated
        else{
            //decrease unrated participants
            setParticipantsUnrated(getParticipantsUnrated()-1);

            if(mcSubmittedAnswer.getSelectedOptions() != null) {
                // decrease unrated answerCounter if answer is selected
                for (AnswerCounter answerCounter: answerCounters) {
                    if(mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())) {
                        answerCounter.setUnRatedCounter(answerCounter.getUnRatedCounter()-1);
                    }
                }
            }
            // decrease unrated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(mcSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter()-1);
            }
        }
    }

}
