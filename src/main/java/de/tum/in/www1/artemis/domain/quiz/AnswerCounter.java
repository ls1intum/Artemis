package de.tum.in.www1.artemis.domain.quiz;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A AnswerCounter.
 */
@Entity
@DiscriminatorValue(value = "AC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerCounter extends QuizStatisticCounter {

    @ManyToOne
    @JsonIgnore
    private MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic;

    @OneToOne(cascade = { CascadeType.PERSIST })
    @JoinColumn(unique = true)
    private AnswerOption answer;

    public MultipleChoiceQuestionStatistic getMultipleChoiceQuestionStatistic() {
        return multipleChoiceQuestionStatistic;
    }

    public void setMultipleChoiceQuestionStatistic(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
    }

    public AnswerOptionDTO getAnswer() {
        return answer;
    }

    public void setAnswer(AnswerOptionDTO answerOption) {
        this.answer = answerOption;
    }

    @Override
    public String toString() {
        return "AnswerCounter{" + "id=" + getId() + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
