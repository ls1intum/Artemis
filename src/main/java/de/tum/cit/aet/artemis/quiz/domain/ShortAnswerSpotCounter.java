package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A ShortAnswerSpotCounter counts, for one spot of a {@link ShortAnswerQuestion}, how often it was answered correctly (rated / unrated).
 * <p>
 * It is no longer a JPA entity in the {@code quiz_statistic_counter} table: short-answer statistics counters are now stored as a JSON list on {@link ShortAnswerQuestionStatistic}
 * (the {@code quiz_statistic.counters} column). This eliminates the eager {@code @OneToMany} counter fan-out when loading a question statistic. It references its spot by the
 * spot's question-scoped id. Mirrors {@link DropLocationCounter}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpotCounter {

    @JsonProperty("spotId")
    private Long spotId;

    @JsonProperty("ratedCounter")
    private int ratedCounter = 0;

    @JsonProperty("unRatedCounter")
    private int unRatedCounter = 0;

    public ShortAnswerSpotCounter() {
    }

    public ShortAnswerSpotCounter(Long spotId) {
        this.spotId = spotId;
    }

    public Long getSpotId() {
        return spotId;
    }

    public void setSpotId(Long spotId) {
        this.spotId = spotId;
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
        return "ShortAnswerSpotCounter{" + "spotId=" + spotId + ", ratedCounter=" + ratedCounter + ", unRatedCounter=" + unRatedCounter + "}";
    }
}
