package de.tum.in.www1.artemis.service.compass.controller;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;

public class AssessmentIndex {

    private Map<Integer, Assessment> modelElementAssessmentMapping;

    public AssessmentIndex() {
        modelElementAssessmentMapping = new ConcurrentHashMap<>();
    }

    /**
     * Get the assessment identified by the given elementID
     *
     * @param elementID The ID of the assessment
     * @return An Optional containing the assessment, if the ID existed. An empty Optional otherwise
     */
    public Optional<Assessment> getAssessment(int elementID) {
        Assessment assessment = modelElementAssessmentMapping.get(elementID);
        return Optional.ofNullable(assessment);
    }

    protected void addAssessment(int elementID, Assessment assessment) {
        modelElementAssessmentMapping.putIfAbsent(elementID, assessment);
    }

    /**
     * Used for statistics. Get the complete map of model element assessments
     *
     * @return The complete map with all model element assessments
     */
    public Map<Integer, Assessment> getAssessmentsMap() {
        return this.modelElementAssessmentMapping;
    }
}
