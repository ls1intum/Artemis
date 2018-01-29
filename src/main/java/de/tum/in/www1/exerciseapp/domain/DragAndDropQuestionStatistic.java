package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DragAndDropQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value="DD")
@JsonTypeName("drag-and-drop")
public class DragAndDropQuestionStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;


    @OneToMany(cascade= CascadeType.ALL, fetch= FetchType.EAGER, orphanRemoval=true, mappedBy = "dragAndDropQuestionStatistic")
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
        return "DragAndDropQuestionStatistic{" +
            "id=" + getId() +
            "}";
    }

    /**
     * 1. creates the DropLocationCounter for the new DropLocation
     *          if where is already an DropLocationCounter with the given DropLocation -> nothing happens
     *
     * @param dropLocation the dropLocation-object which will be added to the DragAndDropQuestionStatistic
     */
    public void addDropLocation(DropLocation dropLocation) {

        if(dropLocation == null) {
            return;
        }

        for(DropLocationCounter counter: dropLocationCounters) {
            if(dropLocation.equals(counter.getDropLocation())) {
                return;
            }
        }
        DropLocationCounter dropLocationCounter = new DropLocationCounter();
        dropLocationCounter.setDropLocation(dropLocation);
        addDropLocationCounters(dropLocationCounter);

    }


    /**
     * 1. check if the Result is rated or unrated
     * 2. increase participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than increase the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise)
     *                                  or unrated  ( participated after the dueDate of the quizExercise)
     */
    @Override
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated) {

        if(submittedAnswer == null) {
            return;
        }

        DragAndDropSubmittedAnswer ddSubmittedAnswer = (DragAndDropSubmittedAnswer)submittedAnswer;

        if(rated) {
            //increase the rated participants
            setParticipantsRated(getParticipantsRated() + 1);

            if(ddSubmittedAnswer.getMappings() != null) {
                // increase rated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter: dropLocationCounters) {
                    if(dropLocationCounter.getDropLocation().isDropLocationCorrect(ddSubmittedAnswer.getMappings())) {
                        dropLocationCounter.setRatedCounter(dropLocationCounter.getRatedCounter() + 1);
                    }
                }
            }
            // increase rated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(ddSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() + 1);
            }
        }
        // Result is unrated
        else{
            //increase the unrated participants
            setParticipantsRated(getParticipantsUnrated() + 1);

            if(ddSubmittedAnswer.getMappings() != null) {
                // increase unrated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter: dropLocationCounters) {
                    if(dropLocationCounter.getDropLocation().isDropLocationCorrect(ddSubmittedAnswer.getMappings())) {
                        dropLocationCounter.setUnRatedCounter(dropLocationCounter.getUnRatedCounter() + 1);
                    }
                }
            }
            // increase unrated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(ddSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter() + 1);
            }
        }
    }

    /**
     * 1. check if the Result is rated or unrated
     * 2. decrease participants, all the DropLocationCounter if the DragAndDropAssignment is correct and if the complete question is correct, than decrease the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all selected answers
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise)
     *                                  or unrated  ( participated after the dueDate of the quizExercise)
     */
    @Override
    public void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated) {

        if(submittedAnswer == null) {
            return;
        }

        DragAndDropSubmittedAnswer ddSubmittedAnswer = (DragAndDropSubmittedAnswer)submittedAnswer;

        if(rated) {
            //decrease the rated participants
            setParticipantsRated(getParticipantsRated() - 1);

            if(ddSubmittedAnswer.getMappings() != null) {
                // decrease rated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter: dropLocationCounters) {
                    if(dropLocationCounter.getDropLocation().isDropLocationCorrect(ddSubmittedAnswer.getMappings())) {
                        dropLocationCounter.setRatedCounter(dropLocationCounter.getRatedCounter() - 1);
                    }
                }
            }
            // decrease rated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(ddSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() - 1);
            }
        }
        // Result is unrated
        else{
            //decrease the unrated participants
            setParticipantsRated(getParticipantsUnrated() - 1);

            if(ddSubmittedAnswer.getMappings() != null) {
                // decrease unrated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter: dropLocationCounters) {
                    if(dropLocationCounter.getDropLocation().isDropLocationCorrect(ddSubmittedAnswer.getMappings())) {
                        dropLocationCounter.setUnRatedCounter(dropLocationCounter.getUnRatedCounter() - 1);
                    }
                }
            }
            // decrease unrated correctCounter if answer is complete correct
            if(getQuestion().isAnswerCorrect(ddSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter() - 1);
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
        for (DropLocationCounter dropLocationCounter: dropLocationCounters) {
            dropLocationCounter.setRatedCounter(0);
            dropLocationCounter.setUnRatedCounter(0);
        }
    }
}
