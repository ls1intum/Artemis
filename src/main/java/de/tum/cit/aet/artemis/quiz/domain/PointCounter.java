package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Counts rated and unrated results for one integer point bucket of a {@link QuizPointStatistic}.
 * <p>
 * Point counters are stored as a JSON list in {@code quiz_statistic.counters}. Their ids are local to the owning statistic and remain stable while the bucket exists.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PointCounter extends DomainObject {

    @JsonProperty("points")
    private double points;

    @JsonProperty("ratedCounter")
    private int ratedCounter;

    @JsonProperty("unRatedCounter")
    private int unRatedCounter;

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = points;
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
        return "PointCounter{" + "id=" + getId() + ", points='" + getPoints() + "'" + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
