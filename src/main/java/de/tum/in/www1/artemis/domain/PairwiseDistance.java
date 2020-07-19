package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.NaturalId;

import java.io.Serializable;

import javax.persistence.*;

/**
 *  Pairwise distance between two TextBlocks
 */
@Entity
@Table(name = "pairwise_distance")
public class PairwiseDistance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @NaturalId
    @Column(name = "block_i", nullable = false)
    private long block_i;

    @NaturalId
    @Column(name = "block_j", nullable = false)
    private long block_j;

    @Column(name = "distance", nullable = false)
    private double distance;

    @ManyToOne
    @JsonIgnore
    private ClusteringResult clusteringResult;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getBlock_i() { return this.block_i; }

    public long getBlock_j() { return block_j; }

    public void setBlock_i(long block_i) { this.block_i = block_i; }

    public void setBlock_j(long block_j) { this.block_j = block_j; }

    public double getDistance() { return distance; }

    public void setDistance(double distance) { this.distance = distance; }

    public ClusteringResult getClusteringResult() { return clusteringResult; }

    public void setClusteringResult(ClusteringResult clusteringResult) { this.clusteringResult = clusteringResult; }

    public PairwiseDistance clusteringResult(ClusteringResult clusteringResult) {
        setClusteringResult(clusteringResult);
        return this;
    }
}
