package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.*;

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
