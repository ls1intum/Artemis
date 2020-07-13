package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.List;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *  Thee clustering result which includes:
 *      - Pairwise distance matrix for text blocks
 *      - List of clusters
 *      - Tree structure of the cluster hierarchy
 */
@Entity
public class ClusteringResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "clusteringResult")
    @JsonIgnore
    private List<PairwiseDistance> pairwiseDistances;

    @OneToMany(mappedBy = "clusteringResult")
    @OrderBy("child")
    @JsonIgnore
    private List<TreeNode> clusterTree;

    @OneToOne(mappedBy = "clusteringResult")
    @JsonIgnore
    private TextExercise exercise;

    public long matrixSize() {
        long size = 0;
        for (PairwiseDistance dist: pairwiseDistances) {
            if (dist.getBlock_i() > size || dist.getBlock_j() > size) {
                size = Long.max(dist.getBlock_i(), dist.getBlock_j());
            }
        }
        return size;
    }

    public double[][] getDistanceMatrix() {
        int matrixSize = (int) matrixSize();
        double[][] distMatrix = new double[matrixSize][matrixSize];
        for(PairwiseDistance dist: pairwiseDistances) {
            int i = (int) dist.getBlock_i();
            int j = (int) dist.getBlock_j();
            distMatrix[i][j] = dist.getDistance();
            distMatrix[j][i] = dist.getDistance();
        }
        return distMatrix;
    }
}
