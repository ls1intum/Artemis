package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

/**
 * A MultipleChoiceQuestion.
 */
@Entity
@DiscriminatorValue(value = "MC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("multiple-choice")
public class MultipleChoiceQuestion extends Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<AnswerOption> answerOptions = new ArrayList<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public List<AnswerOption> getAnswerOptions() {
        return answerOptions;
    }

    public MultipleChoiceQuestion answerOptions(List<AnswerOption> answerOptions) {
        this.answerOptions = answerOptions;
        return this;
    }

    /**
     * 1. add the answerOption to the List of the other answerOptions
     * 2. add backward relation in the answerOption-object
     * 3. add the new answer-option to the MultipleChoiceQuestionStatistic
     *
     * @param answerOption the answerOption object which will be added
     * @return this MultipleChoiceQuestion-object
     */
    public MultipleChoiceQuestion addAnswerOptions(AnswerOption answerOption) {
        this.answerOptions.add(answerOption);
        answerOption.setQuestion(this);
        //if an answerOption was added then add the associated AnswerCounter implicitly
        ((MultipleChoiceQuestionStatistic)getQuestionStatistic()).addAnswerOption(answerOption);
        return this;
    }

    /**
     * 1. delete the new answer-option in the MultipleChoiceQuestionStatistic
     * 2. remove the answerOption from the List of the other answerOptions
     * 3. remove backward relation in the answerOption-object
     *
     * @param answerOption the answerOption object which should be removed
     * @return this MultipleChoiceQuestion-object
     */
    public MultipleChoiceQuestion removeAnswerOptions(AnswerOption answerOption) {
        //if an answerOption was removed then remove the associated AnswerCounter implicitly
        if (getQuestionStatistic() instanceof MultipleChoiceQuestionStatistic) {
            MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic) getQuestionStatistic();
            AnswerCounter answerCounterToDelete = null;
            for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
                if (answerOption.equals(answerCounter.getAnswer())) {
                    answerCounter.setAnswer(null);
                    answerCounterToDelete = answerCounter;
                }
            }
            mcStatistic.getAnswerCounters().remove(answerCounterToDelete);
        }
        this.answerOptions.remove(answerOption);
        answerOption.setQuestion(null);
        return this;

    }

    /**
     * 1. check if the questionStatistic is an instance of MultipleChoiceQuestionStatistic.
     *              otherwise generate a MultipleChoiceQuestionStatistic new replace the old one
     * 2. set the answerOption List to the new answerOption List
     * 3. if an answerOption was added then add the associated AnswerCounter
     * 4. if an answerOption was removed then remove the associated AnswerCounter
     *
     * @param answerOptions the new List of answerOption objects which will be set
     */
    public void setAnswerOptions(List<AnswerOption> answerOptions) {
        MultipleChoiceQuestionStatistic mcStatistic;
        if (getQuestionStatistic() instanceof MultipleChoiceQuestionStatistic) {
            mcStatistic = (MultipleChoiceQuestionStatistic)getQuestionStatistic();
        }
        else{
            mcStatistic = new MultipleChoiceQuestionStatistic();
            setQuestionStatistic(mcStatistic);
        }
        this.answerOptions = answerOptions;

        //if an answerOption was added then add the associated AnswerCounter implicitly
        for (AnswerOption answerOption : getAnswerOptions()) {
            ((MultipleChoiceQuestionStatistic) getQuestionStatistic()).addAnswerOption(answerOption);
        }

        //if an answerOption was removed then remove the associated AnswerCounters implicitly
        Set<AnswerCounter> answerCounterToDelete = new HashSet<>();
        for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
            if (answerCounter.getId() != null) {
                if(!(answerOptions.contains(answerCounter.getAnswer()))) {
                    answerCounter.setAnswer(null);
                    answerCounterToDelete.add(answerCounter);
                }
            }
        }
        mcStatistic.getAnswerCounters().removeAll(answerCounterToDelete);

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
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) o;
        if (multipleChoiceQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), multipleChoiceQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MultipleChoiceQuestion{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", text='" + getText() + "'" +
            ", hint='" + getHint() + "'" +
            ", explanation='" + getExplanation() + "'" +
            ", score='" + getScore() + "'" +
            ", scoringType='" + getScoringType() + "'" +
            ", randomizeOrder='" + isRandomizeOrder() + "'" +
            ", exerciseTitle='" + ((getExercise() == null) ? null : getExercise().getTitle()) + "'" +
            "}";
    }

    /**
     * Constructor.
     *
     * 1. generate associated MultipleChoiceQuestionStatistic implicitly
     */
    public MultipleChoiceQuestion() {
        //create associated Statistic implicitly
        MultipleChoiceQuestionStatistic mcStatistic = new MultipleChoiceQuestionStatistic();
        setQuestionStatistic(mcStatistic);
        mcStatistic.setQuestion(this);

    }
}
