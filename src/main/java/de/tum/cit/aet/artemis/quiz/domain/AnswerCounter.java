package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

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

    @Column(name = "answer_id")
    private Long answerOptionId;

    public MultipleChoiceQuestionStatistic getMultipleChoiceQuestionStatistic() {
        return multipleChoiceQuestionStatistic;
    }

    public void setMultipleChoiceQuestionStatistic(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
    }

    /**
     * Resolves the stored question-scoped answer option ID to the JSON-owned answer option.
     *
     * @return the referenced answer option, or null if the statistic is not connected to a multiple-choice question
     */
    @Transient
    public AnswerOption getAnswer() {
        if (multipleChoiceQuestionStatistic == null || !(multipleChoiceQuestionStatistic.getQuizQuestion() instanceof MultipleChoiceQuestion question)) {
            return null;
        }
        AnswerOption answerOption = question.findAnswerOptionById(answerOptionId);
        if (answerOption == null) {
            throw new IllegalStateException("Answer counter " + getId() + " references missing answer option " + answerOptionId + " in question " + question.getId());
        }
        return answerOption;
    }

    public void setAnswer(AnswerOption answerOption) {
        this.answerOptionId = answerOption == null ? null : answerOption.getId();
    }

    @JsonIgnore
    public Long getAnswerOptionId() {
        return answerOptionId;
    }

    @JsonIgnore
    public void setAnswerOptionId(Long answerOptionId) {
        this.answerOptionId = answerOptionId;
    }

    @JsonIgnore
    public void setQuizQuestionStatistic(MultipleChoiceQuestionStatistic quizQuestionStatistic) {
        setMultipleChoiceQuestionStatistic(quizQuestionStatistic);
    }

    @JsonIgnore
    public AnswerOption getQuizQuestionComponent() {
        return getAnswer();
    }

    @JsonIgnore
    public void setQuizQuestionComponent(AnswerOption answerOption) {
        setAnswer(answerOption);
    }

    @Override
    public String toString() {
        return "AnswerCounter{" + "id=" + getId() + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
