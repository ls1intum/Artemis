package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AssessmentIndex {

    private Map<Integer, Assessment> modelElementAssessmentMapping;

    public AssessmentIndex() {
        modelElementAssessmentMapping = new HashMap<>();
    }

    public Optional<Assessment> getAssessment(int elementID) {
        Assessment assessment = modelElementAssessmentMapping.get(elementID);
        if(assessment == null){
            return Optional.empty();
        }
        return Optional.of(assessment);
    }

    protected void addAssessment(int elementID, Assessment assessment) {
        modelElementAssessmentMapping.putIfAbsent(elementID, assessment);
    }

    /**
     * Used for statistic
     */
    public Map<Integer, Assessment> getAssessmentsMap() {
        return this.modelElementAssessmentMapping;
    }
}
