package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A AnswerCounter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerCounter extends QuizStatisticCounter implements QuizQuestionStatisticComponent<MultipleChoiceQuestionStatistic, AnswerOption, MultipleChoiceQuestion> {

    @JsonIgnore
    private MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic;

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

    }

    @Override
    @JsonIgnore
    public AnswerOption getQuizQuestionComponent() {
        return null;
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionComponent(AnswerOption answerOption) {

    }

    @Override
    public String toString() {
        return "AnswerCounter{" + "id=" + getId() + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
