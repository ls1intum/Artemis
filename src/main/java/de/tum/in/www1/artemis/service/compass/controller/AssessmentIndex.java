package de.tum.in.www1.artemis.service.compass.controller;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.in.www1.artemis.service.compass.assessment.SimilaritySetAssessment;

/**
 * The assessment index contains the assessments for all the similarity sets. It manages the assessments in a hash map that maps from similarityId to the assessment of the
 * corresponding similarity set. A similarity set assessment contains the feedback items and a total score for the elements of the similarity set, i.e. the elements with the same
 * similarityId.
 */
public class AssessmentIndex {

    private Map<Integer, SimilaritySetAssessment> similarityIdAssessmentMapping;

    public AssessmentIndex() {
        similarityIdAssessmentMapping = new ConcurrentHashMap<>();
    }

    /**
     * Get the assessment for the similarity set with the given similarityId.
     *
     * @param similarityId the ID of the similarity set
     * @return an Optional containing the assessment if the similarity ID exists, an empty Optional otherwise
     */
    public Optional<SimilaritySetAssessment> getAssessmentForSimilaritySet(int similarityId) {
        SimilaritySetAssessment similaritySetAssessment = similarityIdAssessmentMapping.get(similarityId);
        return Optional.ofNullable(similaritySetAssessment);
    }

    /**
     * Add a new assessment for the similarity set with the given ID to the similarityId assessment mapping.
     *
     * @param similarityId the ID of the corresponding similarity set
     * @param similaritySetAssessment the assessment for the corresponding similarity set
     */
    protected void addSimilaritySetAssessment(int similarityId, SimilaritySetAssessment similaritySetAssessment) {
        similarityIdAssessmentMapping.putIfAbsent(similarityId, similaritySetAssessment);
    }

    /**
     * Used for statistics. Get the complete map of similarity set assessments.
     *
     * @return The complete map with all similarity set assessments
     */
    public Map<Integer, SimilaritySetAssessment> getAssessmentMap() {
        return this.similarityIdAssessmentMapping;
    }
}
