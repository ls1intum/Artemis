package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScoreDistribution {

    private final long amount;

    private final double score;

    public ScoreDistribution(Long amount, Double score) {
        this.amount = amount;
        this.score = score != null ? score : 0;
    }

    public long getAmount() {
        return amount;
    }

    public double getScore() {
        return score;
    }
}
