package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An AnswerCounter counts, for one answer option of a {@link MultipleChoiceQuestion}, how often it was selected (rated / unrated).
 * <p>
 * It is no longer a JPA entity in the {@code quiz_statistic_counter} table: multiple-choice statistics counters are now stored as a JSON list on
 * {@link MultipleChoiceQuestionStatistic} (the {@code quiz_statistic.counters} column). This eliminates the eager {@code @OneToMany} counter fan-out when loading a question
 * statistic. It references its answer option by the option's question-scoped id. Mirrors {@link DropLocationCounter}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerCounter {

    @JsonProperty("answerId")
    private Long answerId;

    @JsonProperty("ratedCounter")
    private int ratedCounter = 0;

    @JsonProperty("unRatedCounter")
    private int unRatedCounter = 0;

    public AnswerCounter() {
    }

    public AnswerCounter(Long answerId) {
        this.answerId = answerId;
    }

    public Long getAnswerId() {
        return answerId;
    }

    public void setAnswerId(Long answerId) {
        this.answerId = answerId;
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
        return "AnswerCounter{" + "answerId=" + answerId + ", rated=" + ratedCounter + ", unrated=" + unRatedCounter + "}";
    }
}
