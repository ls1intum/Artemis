package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DragAndDropQuestionStatistic.
 * <p>
 * Its per-drop-location counters are stored as a JSON list in the {@code quiz_statistic.counters} column (see {@link DropLocationCounter}) instead of separate
 * {@code quiz_statistic_counter} rows, eliminating the eager {@code @OneToMany} counter fan-out. Counters are fully recomputed from the results on every statistics update, so no
 * per-counter locking is required. {@link #getDropLocationCounters()} keeps its signature/shape so the REST/websocket wire format is preserved.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropQuestionStatistic extends QuizQuestionStatistic {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters")
    private List<DropLocationCounter> dropLocationCounters = new ArrayList<>();

    public List<DropLocationCounter> getDropLocationCounters() {
        return dropLocationCounters;
    }

    public void addDropLocationCounters(DropLocationCounter dropLocationCounter) {
        this.dropLocationCounters.add(dropLocationCounter);
    }

    public void setDropLocationCounters(List<DropLocationCounter> dropLocationCounters) {
        this.dropLocationCounters = dropLocationCounters != null ? dropLocationCounters : new ArrayList<>();
    }

    /**
     * 1. creates the DropLocationCounter for the new DropLocation if there is already a DropLocationCounter with the given DropLocation -> nothing happens
     *
     * @param dropLocation the dropLocation-object which will be added to the DragAndDropQuestionStatistic
     */
    public void addDropLocation(DropLocation dropLocation) {

        if (dropLocation == null || dropLocation.getId() == null) {
            return;
        }

        for (DropLocationCounter counter : dropLocationCounters) {
            if (dropLocation.getId().equals(counter.getDropLocationId())) {
                return;
            }
        }
        DropLocationCounter dropLocationCounter = new DropLocationCounter();
        dropLocationCounter.setDropLocationId(dropLocation.getId());
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
        DragAndDropQuestion question = getQuizQuestion() instanceof DragAndDropQuestion dragAndDropQuestion ? dragAndDropQuestion : null;

        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);

            if (question != null) {
                // change rated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
                    DropLocation dropLocation = question.findDropLocationById(dropLocationCounter.getDropLocationId());
                    if (dropLocation != null && question.isDropLocationCorrect(dropLocation, ddSubmittedAnswer)) {
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

            if (question != null) {
                // change unrated dropLocationCounter if dropLocation is correct
                for (DropLocationCounter dropLocationCounter : dropLocationCounters) {
                    DropLocation dropLocation = question.findDropLocationById(dropLocationCounter.getDropLocationId());
                    if (dropLocation != null && question.isDropLocationCorrect(dropLocation, ddSubmittedAnswer)) {
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
