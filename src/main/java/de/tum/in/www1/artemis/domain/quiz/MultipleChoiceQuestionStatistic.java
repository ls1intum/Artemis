package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A MultipleChoiceQuestionStatistic.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceQuestionStatistic extends QuizQuestionStatistic {

    private Set<AnswerCounter> answerCounters = new HashSet<>();

    public Set<AnswerCounter> getAnswerCounters() {
        return answerCounters;
    }

    public void addAnswerCounters(AnswerCounter answerCounter) {
        this.answerCounters.add(answerCounter);
        answerCounter.setMultipleChoiceQuestionStatistic(this);
    }

    public void setAnswerCounters(Set<AnswerCounter> answerCounters) {
        this.answerCounters = answerCounters;
    }

    /**
     * 1. creates the AnswerCounter for the new AnswerOption if where is already an AnswerCounter with the given answerOption -> nothing happens
     *
     * @param answer the answer object which will be added to the MultipleChoiceStatistic
     */
    public void addAnswerOption(AnswerOption answer) {
        if (answer == null) {
            return;
        }
        for (AnswerCounter counter : answerCounters) {
            if (answer.equals(counter.getAnswer())) {
                return;
            }
        }
        AnswerCounter answerCounter = new AnswerCounter();
        answerCounter.setAnswer(answer);
        addAnswerCounters(answerCounter);
    }

    /**
     * 1. check if the Result is rated or unrated 2. change participants, all selected AnswerCounter and if the question is correct, than change the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                            of the quizExercise)
     * @param change          the int-value, which will be added to the Counter and participants
     * @param quizQuestion
     */
    @Override
    protected void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change, QuizQuestion quizQuestion) {
        if (!(submittedAnswer instanceof MultipleChoiceSubmittedAnswer mcSubmittedAnswer)) {
            return;
        }

        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);

            if (mcSubmittedAnswer.getSelectedOptions() != null) {
                // change rated answerCounter if answer is selected
                for (AnswerCounter answerCounter : answerCounters) {
                    if (mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())) {
                        answerCounter.setRatedCounter(answerCounter.getRatedCounter() + change);
                    }
                }
            }
            // change rated correctCounter if answer is complete correct
            if (quizQuestion.isAnswerCorrect(mcSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() + change);
            }
        }
        // Result is unrated
        else {
            // change the unrated participants
            setParticipantsUnrated(getParticipantsUnrated() + change);

            if (mcSubmittedAnswer.getSelectedOptions() != null) {
                for (AnswerCounter answerCounter : answerCounters) {
                    // change unrated answerCounter if answer is selected
                    if (mcSubmittedAnswer.getSelectedOptions().contains(answerCounter.getAnswer())) {
                        answerCounter.setUnRatedCounter(answerCounter.getUnRatedCounter() + change);
                    }
                }
            }

            // change unrated correctCounter if answer is complete correct
            if (quizQuestion.isAnswerCorrect(mcSubmittedAnswer)) {
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
