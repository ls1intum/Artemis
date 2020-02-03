package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.tum.in.www1.artemis.domain.SubmittedAnswer;

/**
 * A DragAndDropQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonTypeName("drag-and-drop")
public class DragAndDropQuestionStatistic extends QuizQuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "dragAndDropQuestionStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DropLocationCounter> dropLocationCounters = new HashSet<>();

    public Set<DropLocationCounter> getDropLocationCounters() {
        return dropLocationCounters;
    }

    public DragAndDropQuestionStatistic dropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.dropLocationCounters = dropLocationCounters;
        return this;
    }

    public DragAndDropQuestionStatistic addDropLocationCounters(DropLocationCounter dropLocationCounter) {
        this.dropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(this);
        return this;
    }

    public DragAndDropQuestionStatistic removeDropLocationCounters(DropLocationCounter dropLocationCounter) {
        this.dropLocationCounters.remove(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(null);
        return this;
    }

    public void setDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.dropLocationCounters = dropLocationCounters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic = (DragAndDropQuestionStatistic) o;
        if (dragAndDropQuestionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropQuestionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropQuestionStatistic{" + "id=" + getId() + "}";
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
     * increase participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than increase the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                        of the quizExercise)
     */
    @Override
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
    @Override
    public void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated) {

        changeStatisticBasedOnResult(submittedAnswer, rated, -1);
    }

    /**
     * 1. check if the Result is rated or unrated 2. change participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is
     * correct, than change the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                        of the quizExercise)
     * @param change          the int-value, which will be added to the Counter and participants
     */
    private void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change) {

        if (submittedAnswer == null) {
            return;
        }

        DragAndDropSubmittedAnswer ddSubmittedAnswer = (DragAndDropSubmittedAnswer) submittedAnswer;

        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);

            if (ddSubmittedAnswer.getMappings() != null) {
                // change rated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
                    if (dropLocationCounter.getDropLocation().isDropLocationCorrect(ddSubmittedAnswer)) {
                        dropLocationCounter.setRatedCounter(dropLocationCounter.getRatedCounter() + change);
                    }
                }
            }
            // change rated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(ddSubmittedAnswer)) {
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
                    if (dropLocationCounter.getDropLocation().isDropLocationCorrect(ddSubmittedAnswer)) {
                        dropLocationCounter.setUnRatedCounter(dropLocationCounter.getUnRatedCounter() + change);
                    }
                }
            }
            // change unrated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(ddSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter() + change);
            }
        }
    }

    /**
     * reset all counters to 0
     */
    @Override
    public void resetStatistic() {
        setParticipantsRated(0);
        setParticipantsUnrated(0);
        setRatedCorrectCounter(0);
        setUnRatedCorrectCounter(0);
        for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
            dropLocationCounter.setRatedCounter(0);
            dropLocationCounter.setUnRatedCounter(0);
        }
    }
}
