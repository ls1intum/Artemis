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

    /**
     * increase participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than increase the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                        of the quizExercise)
     */
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated) {
        changeStatisticBasedOnResult(submittedAnswer, rated, 1);
    }

    /**
     * decrease participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than decrease the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                        of the quizExercise)
     */
    public void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated) {
        changeStatisticBasedOnResult(submittedAnswer, rated, -1);
    }

    protected abstract void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change);

    /**
     * reset the general statistics
     */
    public void resetStatistic() {
        setParticipantsRated(0);
        setParticipantsUnrated(0);
        setRatedCorrectCounter(0);
        setUnRatedCorrectCounter(0);
    }
}
