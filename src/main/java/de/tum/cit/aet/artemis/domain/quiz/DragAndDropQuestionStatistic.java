package de.tum.cit.aet.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DragAndDropQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropQuestionStatistic extends QuizQuestionStatistic {

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "dragAndDropQuestionStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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
     */
    @Override
    protected void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change) {
        if (!(submittedAnswer instanceof DragAndDropSubmittedAnswer ddSubmittedAnswer)) {
            return;
        }

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
        super.resetStatistic();
        for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
            dropLocationCounter.setRatedCounter(0);
            dropLocationCounter.setUnRatedCounter(0);
        }
    }
}
