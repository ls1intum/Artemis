package de.tum.in.www1.artemis.service;

import java.util.Optional;

import de.tum.in.www1.artemis.domain.*;

public interface TextAssessmentUtilities {

    /**
     * Calculates the variance for the cluster of a text submission and returns its score
     * @param textSubmission
     * @return {double} variance of the cluster
     */
    public Optional<Double> determineVariance(TextBlock textBlock);

    /**
     * Calculates the expectation value for the cluster of a text submission and returns its score
     * @param textBlock
     * @return {double} expectation of the cluster
     */
    public Optional<Double> determineExpectation(TextBlock textBlock);

    /**
     * Calculates the standard deviation for the cluster of a text submission and returns its score
     * @param textBlock
     * @return {double} variance of the cluster
     */
    public Optional<Double> determineStandardDeviation(TextBlock textBlock);

    /**
     * Calculates the percentage of elements in a cluster which have the same score
     * @param textBlock text submission for which a cluster is assessed
     * @return
     */
    public Optional<Double> determineCoveragePercentage(TextBlock textBlock);

    /**
     * Calculates the percentage of elements in a cluster which are graded
     * @param textSubmission
     * @return
     */
    public Optional<Double> determineScoreCoveragePercentage(TextBlock textBlock);

    /**
     *
     * @param textSubmission
     * @return
     */
    public Optional<Double> determineAverage(TextBlock textBlock);

    /**
     *
     * @param textBlock
     * @return
     */
    public Integer determineClusterSize(TextBlock textBlock);
}
