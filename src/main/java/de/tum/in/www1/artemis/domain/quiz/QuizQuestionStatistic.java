package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.view.QuizView;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceQuestionStatistic.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropQuestionStatistic.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerQuestionStatistic.class, name = "short-answer")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizQuestionStatistic implements QuizQuestionComponent<QuizQuestion>, Serializable {

    @JsonView(QuizView.Before.class)
    private Long id;

    private Integer participantsRated = 0;

    private Integer participantsUnrated = 0;

    private Integer ratedCorrectCounter = 0;

    private Integer unRatedCorrectCounter = 0;

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

    @JsonIgnore
    public void setQuestion(QuizQuestion quizQuestion) {

    }

    /**
     * increase participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than increase the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                            of the quizExercise)
     * @param quizQuestion    quiz question object statistics belongs to
     */
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated, QuizQuestion quizQuestion) {
        changeStatisticBasedOnResult(submittedAnswer, rated, 1, quizQuestion);
    }

    /**
     * decrease participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than decrease the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                            of the quizExercise)
     * @param quizQuestion    quiz question object statistics belongs to
     */
    public void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated, QuizQuestion quizQuestion) {
        changeStatisticBasedOnResult(submittedAnswer, rated, -1, quizQuestion);
    }

    protected abstract void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change, QuizQuestion quizQuestion);

    /**
     * reset the general statistics
     */
    public void resetStatistic() {
        setParticipantsRated(0);
        setParticipantsUnrated(0);
        setRatedCorrectCounter(0);
        setUnRatedCorrectCounter(0);
    }

    @Override
    public String toString() {
        return getClass() + "{" + "ratedCorrectCounter=" + ratedCorrectCounter + ", unRatedCorrectCounter=" + unRatedCorrectCounter + "participantsRated=" + getParticipantsRated()
                + ", participantsUnrated=" + getParticipantsUnrated() + '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getParticipantsRated() {
        return participantsRated;
    }

    public void setParticipantsRated(Integer participantsRated) {
        this.participantsRated = participantsRated;
    }

    public Integer getParticipantsUnrated() {
        return participantsUnrated;
    }

    public void setParticipantsUnrated(Integer participantsUnrated) {
        this.participantsUnrated = participantsUnrated;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        QuizQuestionStatistic quizQuestionStatistic = (QuizQuestionStatistic) obj;
        if (quizQuestionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizQuestionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
