package de.tum.cit.aet.artemis.domain.quiz;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A AnswerCounter.
 */
@Entity
@DiscriminatorValue(value = "AC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerCounter extends QuizStatisticCounter implements QuizQuestionStatisticComponent<MultipleChoiceQuestionStatistic, AnswerOption, MultipleChoiceQuestion> {

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

    public AnswerOption getAnswer() {
        return answer;
    }

    public void setAnswer(AnswerOption answerOption) {
        this.answer = answerOption;
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionStatistic(MultipleChoiceQuestionStatistic quizQuestionStatistic) {
        setMultipleChoiceQuestionStatistic(quizQuestionStatistic);
    }

    @Override
    @JsonIgnore
    public AnswerOption getQuizQuestionComponent() {
        return getAnswer();
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionComponent(AnswerOption answerOption) {
        setAnswer(answerOption);
    }

    @Override
    public String toString() {
        return "AnswerCounter{" + "id=" + getId() + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
