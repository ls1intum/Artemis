package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Counts rated and unrated results for one integer point bucket of a {@link QuizPointStatistic}.
 * <p>
 * Point counters are stored as a JSON list in {@code quiz_statistic.counters}. Their ids are local to the owning statistic and remain stable while the bucket exists. Equality
 * includes every persisted value so that Hibernate detects in-place counter changes when dirty-checking the JSON column.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PointCounter {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("points")
    private double points;

    @JsonProperty("ratedCounter")
    private int ratedCounter;

    @JsonProperty("unRatedCounter")
    private int unRatedCounter;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PointCounter pointCounter = (PointCounter) obj;
        if (id == null || pointCounter.id == null) {
            return false;
        }
        return Double.compare(points, pointCounter.points) == 0 && ratedCounter == pointCounter.ratedCounter && unRatedCounter == pointCounter.unRatedCounter
                && Objects.equals(id, pointCounter.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, points, ratedCounter, unRatedCounter);
    }

    @Override
    public String toString() {
        return "PointCounter{" + "id=" + getId() + ", points='" + getPoints() + "'" + ", rated=" + getRatedCounter() + ", unrated=" + getUnRatedCounter() + "}";
    }
}
