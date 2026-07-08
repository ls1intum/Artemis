package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A DropLocationCounter counts, for one drop location of a {@link DragAndDropQuestion}, how often it was answered correctly (rated / unrated).
 * <p>
 * It is no longer a JPA entity in the {@code quiz_statistic_counter} table: drag-and-drop statistics counters are now stored as a JSON list on
 * {@link DragAndDropQuestionStatistic} (the {@code quiz_statistic.counters} column). This eliminates the eager {@code @OneToMany} counter fan-out when loading a question
 * statistic.
 * It references its drop location by the drop location's question-scoped id.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocationCounter {

    @JsonProperty("dropLocationId")
    private Long dropLocationId;

    @JsonProperty("ratedCounter")
    private int ratedCounter = 0;

    @JsonProperty("unRatedCounter")
    private int unRatedCounter = 0;

    public DropLocationCounter() {
    }

    public DropLocationCounter(Long dropLocationId) {
        this.dropLocationId = dropLocationId;
    }

    public Long getDropLocationId() {
        return dropLocationId;
    }

    public void setDropLocationId(Long dropLocationId) {
        this.dropLocationId = dropLocationId;
    }

    public int getRatedCounter() {
        return ratedCounter;
    }

    public void setRatedCounter(int ratedCounter) {
        this.ratedCounter = ratedCounter;
    }

    public int getUnRatedCounter() {
        return unRatedCounter;
    }

    public void setUnRatedCounter(int unRatedCounter) {
        this.unRatedCounter = unRatedCounter;
    }

    @Override
    public String toString() {
        return "DropLocationCounter{" + "dropLocationId=" + dropLocationId + ", ratedCounter=" + ratedCounter + ", unRatedCounter=" + unRatedCounter + "}";
    }
}
