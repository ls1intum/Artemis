package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.*;

@Entity
@DiscriminatorValue(value = "Q")
@DiscriminatorOptions(force = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionStatistic.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionStatistic.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionStatistic.class, name = "short-answer") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizQuestionStatistic extends QuizStatistic {

    @Column(name = "rated_correct_counter")
    private Integer ratedCorrectCounter = 0;

    @Column(name = "un_rated_correct_counter")
    private Integer unRatedCorrectCounter = 0;

    @OneToOne(mappedBy = "quizQuestionStatistic")
    @JsonIgnore
    private QuizQuestion quizQuestion;

    public Integer getRatedCorrectCounter() {
        return ratedCorrectCounter;
    }

    public void setRatedCorrectCounter(Integer ratedCorrectCounter) {
        this.ratedCorrectCounter = ratedCorrectCounter;
    }

    public Integer getUnRatedCorrectCounter() {
        return unRatedCorrectCounter;
    }

    public void setUnRatedCorrectCounter(Integer unRatedCorrectCounter) {
        this.unRatedCorrectCounter = unRatedCorrectCounter;
    }

    public QuizQuestion getQuizQuestion() {
        return quizQuestion;
    }

    public void setQuizQuestion(QuizQuestion quizQuestion) {
        this.quizQuestion = quizQuestion;
    }

    public abstract void addResult(SubmittedAnswer submittedAnswer, boolean rated);

    public abstract void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated);

    public abstract void resetStatistic();
}
