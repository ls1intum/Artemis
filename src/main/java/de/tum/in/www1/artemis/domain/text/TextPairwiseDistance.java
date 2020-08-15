package de.tum.in.www1.artemis.domain.text;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

import javax.persistence.*;

/**
 *  Pairwise distance between two TextBlocks
 */
@Entity
@Table(name = "text_pairwise_distance")
public class TextPairwiseDistance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "block_i", nullable = false)
    private long block_i;

    @Column(name = "block_j", nullable = false)
    private long block_j;

    @Column(name = "distance", nullable = false)
    private double distance;

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getBlock_i() { return this.block_i; }

    public long getBlock_j() { return block_j; }

    public void setBlock_i(long block_i) { this.block_i = block_i; }

    public void setBlock_j(long block_j) { this.block_j = block_j; }

    public double getDistance() { return distance; }

    public void setDistance(double distance) { this.distance = distance; }

    public TextExercise getExercise() { return exercise; }

    public void setExercise(TextExercise exercise) { this.exercise = exercise; }

    public TextPairwiseDistance exercise(TextExercise exercise) {
        setExercise(exercise);
        return this;
    }
}
