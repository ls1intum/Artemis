package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A AnswerCounter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerCounter extends TempIdObject implements QuizQuestionStatisticComponent<MultipleChoiceQuestionStatistic, AnswerOption, MultipleChoiceQuestion> {

    @JsonIgnore
    private MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic;

    private AnswerOption answer;

    private Integer ratedCounter = 0;

    private Integer unRatedCounter = 0;

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

    public Integer getRatedCounter() {
        return ratedCounter;
    }

    public void setRatedCounter(Integer ratedCounter) {
        this.ratedCounter = ratedCounter;
    }

    public Integer getUnRatedCounter() {
        return unRatedCounter;
    }

    public void setUnRatedCounter(Integer unRatedCounter) {
        this.unRatedCounter = unRatedCounter;
    }
}
