package de.tum.in.www1.artemis.service;

import java.util.Optional;

import de.tum.in.www1.artemis.domain.*;

public interface TextAssessmentUtilities {

    /**
     * Calculates the variance for the cluster of a text submission and returns its score
     * @param textCluster
     * @return {double} variance of the cluster
     */
    public Optional<Double> calculateVariance(TextCluster textCluster);

    /**
     * Calculates the expectation value for the cluster of a text submission and returns its score
     * @param textCluster
     * @return {double} expectation of the cluster
     */
    public Optional<Double> calculateExpectation(TextCluster textCluster);

    /**
     * Calculates the standard deviation for the cluster of a text submission and returns its score
     * @param textCluster
     * @return {double} variance of the cluster
     */
    public Optional<Double> calculateStandardDeviation(TextCluster textCluster);

    /**
     * Calculates the percentage of elements in a cluster which have the same score
     * @param cluster cluster for which the coverage percentage is calculated
     * @return
     */
    public Optional<Double> determineCoveragePercentage(TextCluster cluster);

    /**
     * Calculates the percentage of elements in a cluster which are graded
     * @param textBlock
     * @return
     */
    public Optional<Double> determineScoreCoveragePercentage(TextBlock textBlock);

    /**
     *
     * @param textCluster
     * @return
     */
    public Optional<Double> determineAverage(TextCluster textCluster);

    /**
     *
     * @param textCluster
     * @return
     */
    public Integer getClusterSize(TextCluster textCluster);

    /**
     *
     * @param textBlock
     * @return
     */
    public Integer getClusterSize(TextBlock textBlock);
}
