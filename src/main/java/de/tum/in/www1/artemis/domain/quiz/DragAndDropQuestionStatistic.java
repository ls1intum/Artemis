package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DragAndDropQuestionStatistic.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropQuestionStatistic extends QuizQuestionStatistic {

    private Set<DropLocationCounter> dropLocationCounters = new HashSet<>();

    public Set<DropLocationCounter> getDropLocationCounters() {
        return dropLocationCounters;
    }

    public void addDropLocationCounters(DropLocationCounter dropLocationCounter) {
        this.dropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(this);
    }

    public void setDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.dropLocationCounters = dropLocationCounters;
    }

    /**
     * 1. creates the DropLocationCounter for the new DropLocation if where is already an DropLocationCounter with the given DropLocation -> nothing happens
     *
     * @param dropLocation the dropLocation-object which will be added to the DragAndDropQuestionStatistic
     */
    public void addDropLocation(DropLocation dropLocation) {

        if (dropLocation == null) {
            return;
        }

        for (DropLocationCounter counter : dropLocationCounters) {
            if (dropLocation.equals(counter.getDropLocation())) {
                return;
            }
        }
        DropLocationCounter dropLocationCounter = new DropLocationCounter();
        dropLocationCounter.setDropLocation(dropLocation);
        addDropLocationCounters(dropLocationCounter);
    }

    /**
     * 1. check if the Result is rated or unrated 2. change participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is
     * correct, than change the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                            of the quizExercise)
     * @param change          the int-value, which will be added to the Counter and participants
     * @param quizQuestion    quiz question object statistics belongs to
     */
    @Override
    protected void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change, QuizQuestion quizQuestion) {
        if (!(submittedAnswer instanceof DragAndDropSubmittedAnswer ddSubmittedAnswer)) {
            return;
        }
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizQuestion;
        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);

            if (ddSubmittedAnswer.getMappings() != null) {
                // change rated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
                    if (dragAndDropQuestion.isDropLocationCorrect(ddSubmittedAnswer, dropLocationCounter.getDropLocation())) {
                        dropLocationCounter.setRatedCounter(dropLocationCounter.getRatedCounter() + change);
                    }
                }
            }
            // change rated correctCounter if answer is complete correct
            if (dragAndDropQuestion.isAnswerCorrect(ddSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() + change);
            }
        }
        // Result is unrated
        else {
            // change the unrated participants
            setParticipantsUnrated(getParticipantsUnrated() + change);

            if (ddSubmittedAnswer.getMappings() != null) {
                // change unrated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
                    if (dragAndDropQuestion.isDropLocationCorrect(ddSubmittedAnswer, dropLocationCounter.getDropLocation())) {
                        dropLocationCounter.setUnratedCounter(dropLocationCounter.getUnratedCounter() + change);
                    }
                }
            }
            // change unrated correctCounter if answer is complete correct
            if (dragAndDropQuestion.isAnswerCorrect(ddSubmittedAnswer)) {
                setUnratedCorrectCounter(getUnratedCorrectCounter() + change);
            }
        }
    }

    /**
     * reset all counters to 0
     */
    @Override
    public void resetStatistic() {
        super.resetStatistic();
        for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
            dropLocationCounter.setRatedCounter(0);
            dropLocationCounter.setUnratedCounter(0);
        }
    }
}
