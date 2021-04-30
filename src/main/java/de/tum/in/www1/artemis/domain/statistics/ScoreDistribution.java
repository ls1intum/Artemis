package de.tum.in.www1.artemis.domain.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScoreDistribution {

    private final long amount;

    private final double score;

    public ScoreDistribution(long amount, double score) {
        this.amount = amount;
        this.score = score;
    }

    public long getAmount() {
        return amount;
    }

    public double getScore() {
        return score;
    }
}
