package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;

public interface TextAssessmentUtilities {

    /**
     * Calculates the variance for the cluster of a text submission and returns its score
     * @param textSubmission
     * @return {double} variance of the cluster
     */
    public double determineVariance(TextSubmission textSubmission);

    /**
     * Calculates the expectation value for the cluster of a text submission and returns its score
     * @param textSubmission
     * @return {double} expectation of the cluster
     */
    public double determineExpectation(TextSubmission textSubmission);

    /**
     * Calculates the standard deviation for the cluster of a text submission and returns its score
     * @param textSubmission
     * @return {double} variance of the cluster
     */
    public double determineStandardDeviation(TextSubmission textSubmission);

    /**
     * Calculates the percentage of elements in a cluster which have the same score
     * @param textSubmission text submission for which a cluster is assessed
     * @param result
     * @return
     */
    public double determineCoveragePercentage(TextSubmission textSubmission);

    /**
     * Calculates the percentage of elements in a cluster which are graded
     * @param textSubmission
     * @return
     */
    public double determineScoreCoveragePercentage(TextSubmission textSubmission, TextBlock textBlock);

    /**
     *
     * @param textSubmission
     * @return
     */
    public double determineAverage(TextSubmission textSubmission);
}
