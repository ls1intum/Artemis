package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

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

    public MultipleChoiceQuestion addAnswerOptions(AnswerOption answerOption) {
        this.answerOptions.add(answerOption);
        answerOption.setQuestion(this);
        ((MultipleChoiceQuestionStatistic)getQuestionStatistic()).addAnswerOption(answerOption);
        return this;
    }

    public MultipleChoiceQuestion removeAnswerOptions(AnswerOption answerOption) {
        MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic)getQuestionStatistic();

        AnswerCounter delete = null;

        for(AnswerCounter answerCounter: mcStatistic.getAnswerCounters())
            if(answerOption.equals(answerCounter.getAnswer())){
            answerCounter.setAnswer(null);
            delete = answerCounter;
            }

        mcStatistic.getAnswerCounters().remove(delete);
        this.answerOptions.remove(answerOption);
        answerOption.setQuestion(null);
        return this;
    }

    public void setAnswerOptions(List<AnswerOption> answerOptions) {
        MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic)getQuestionStatistic();

        this.answerOptions = answerOptions;
        for (AnswerOption answerOption : getAnswerOptions()) {
            ((MultipleChoiceQuestionStatistic)getQuestionStatistic()).addAnswerOption(answerOption);
        }
        //delete old AnswerCounter
        Set<AnswerCounter> delete = new HashSet<>();
        for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
            if (answerCounter.getId() != null) {
                if(!(answerOptions.contains(answerCounter.getAnswer()))){
                    answerCounter.setAnswer(null);
                    delete.add(answerCounter);
                }
            }
        }

        mcStatistic.getAnswerCounters().removeAll(delete);
    }
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public ScoringStrategy getScoringStrategy() {
        switch (getScoringType()) {
            case ALL_OR_NOTHING:
                return new ScoringStrategyAllOrNothing();
            default:
                throw new RuntimeException("Only Scoring Type ALL_OR_NOTHING is implemented yet!");
        }
    }

    @Override
    public boolean isAnswerCorrect(SubmittedAnswer submittedAnswer) {
        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
            MultipleChoiceSubmittedAnswer mcAnswer = (MultipleChoiceSubmittedAnswer) submittedAnswer;
            // iterate through each answer option and compare its correctness with the answer's selection
            for (AnswerOption answerOption : getAnswerOptions()) {
                boolean isSelected = false;
                // search for this answer option in the selected answer options
                for (AnswerOption selectedOption : mcAnswer.getSelectedOptions()) {
                    if (selectedOption.getId().longValue() == answerOption.getId().longValue()) {
                        // this answer option is selected => we can stop searching
                        isSelected = true;
                        break;
                    }
                }
                // if the user was wrong about this answer option, the entire answer can no longer be 100% correct
                // being wrong means either a correct option is not selected, or an incorrect option is selected
                if ((answerOption.isIsCorrect() && !isSelected) || (!answerOption.isIsCorrect() && isSelected)) {
                    return false;
                }
            }
            // the user wasn't wrong about a single answer option => the answer is 100% correct
            return true;
        } else {
            // the submitted answer's type doesn't fit the question's type => it cannot be correct
            return false;
        }
    }

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

    public MultipleChoiceQuestion(){
        MultipleChoiceQuestionStatistic mcStatistic = new MultipleChoiceQuestionStatistic();
        setQuestionStatistic(mcStatistic);
        mcStatistic.setQuestion(this);

    }
}
