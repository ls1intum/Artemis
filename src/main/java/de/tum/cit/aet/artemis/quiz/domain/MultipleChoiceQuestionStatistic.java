package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A MultipleChoiceQuestionStatistic.
 * <p>
 * Its per-answer-option counters are stored as a JSON list in the {@code quiz_statistic.counters} column (see {@link AnswerCounter}) instead of separate
 * {@code quiz_statistic_counter} rows, eliminating the eager {@code @OneToMany} counter fan-out. Counters are fully recomputed from the results on every statistics update, so no
 * per-counter locking is required. {@link #getAnswerCounters()} keeps its signature/shape so the REST/websocket wire format is preserved. Mirrors
 * {@link DragAndDropQuestionStatistic}.
 */
@Entity
@DiscriminatorValue(value = "MC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceQuestionStatistic extends QuizQuestionStatistic {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters")
    private List<AnswerCounter> answerCounters = new ArrayList<>();

    public List<AnswerCounter> getAnswerCounters() {
        return answerCounters;
    }

    public void addAnswerCounters(AnswerCounter answerCounter) {
        this.answerCounters.add(answerCounter);
    }

    public void setAnswerCounters(List<AnswerCounter> answerCounters) {
        this.answerCounters = answerCounters != null ? answerCounters : new ArrayList<>();
    }

    /**
     * 1. creates the AnswerCounter for the new AnswerOption if there is already an AnswerCounter with the given answerOption -> nothing happens
     *
     * @param answer the answer object which will be added to the MultipleChoiceStatistic
     */
    public void addAnswerOption(AnswerOption answer) {
        if (answer == null || answer.getId() == null) {
            return;
        }
        for (AnswerCounter counter : answerCounters) {
            if (answer.getId().equals(counter.getAnswerId())) {
                return;
            }
        }
        AnswerCounter answerCounter = new AnswerCounter();
        answerCounter.setAnswerId(answer.getId());
        addAnswerCounters(answerCounter);
    }

    /**
     * 1. check if the Result is rated or unrated 2. change participants, all selected AnswerCounter and if the question is correct, than change the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                            of the quizExercise)
     * @param change          the int-value, which will be added to the Counter and participants
     */
    @Override
    protected void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change) {
        if (!(submittedAnswer instanceof MultipleChoiceSubmittedAnswer mcSubmittedAnswer)) {
            return;
        }
        Set<Long> selectedOptionIds = mcSubmittedAnswer.toSelectedIds();

        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);
            // change rated answerCounter if answer is selected
            for (AnswerCounter answerCounter : answerCounters) {
                if (selectedOptionIds.contains(answerCounter.getAnswerId())) {
                    answerCounter.setRatedCounter(answerCounter.getRatedCounter() + change);
                }
            }
            // change rated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(mcSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() + change);
            }
        }
        // Result is unrated
        else {
            // change the unrated participants
            setParticipantsUnrated(getParticipantsUnrated() + change);
            // change unrated answerCounter if answer is selected
            for (AnswerCounter answerCounter : answerCounters) {
                if (selectedOptionIds.contains(answerCounter.getAnswerId())) {
                    answerCounter.setUnRatedCounter(answerCounter.getUnRatedCounter() + change);
                }
            }
            // change unrated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(mcSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter() + change);
            }
        }
    }

    /**
     * reset all counters to 0
     */
    @Override
    public void resetStatistic() {
        super.resetStatistic();
        for (AnswerCounter answerCounter : answerCounters) {
            answerCounter.setRatedCounter(0);
            answerCounter.setUnRatedCounter(0);
        }
    }
}
