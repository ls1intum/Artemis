package de.tum.in.www1.artemis.domain.text;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.in.www1.artemis.domain.DomainObject;

/**
 *  Pairwise distance between two TextBlocks
 */
@Entity
@Table(name = "text_pairwise_distance")
public class TextPairwiseDistance extends DomainObject {

    @Column(name = "block_i", nullable = false)
    private long blockI;

    @Column(name = "block_j", nullable = false)
    private long blockJ;

    @Column(name = "distance", nullable = false)
    private double distance;

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    public long getBlockI() {
        return blockI;
    }

    public long getBlockJ() {
        return blockJ;
    }

    public void setBlockI(long blockI) {
        this.blockI = blockI;
    }

    public void setBlockJ(long blockJ) {
        this.blockJ = blockJ;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public TextExercise getExercise() {
        return exercise;
    }

    public void setExercise(TextExercise exercise) {
        this.exercise = exercise;
    }

    public TextPairwiseDistance exercise(TextExercise exercise) {
        setExercise(exercise);
        return this;
    }
}
