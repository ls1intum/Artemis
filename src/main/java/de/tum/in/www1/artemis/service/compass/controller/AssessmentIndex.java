package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.HashMap;
import java.util.Map;

public class AssessmentIndex {

    private Map<Integer, Assessment> modelElementAssessmentMapping;

    public AssessmentIndex() {
        modelElementAssessmentMapping = new HashMap<>();
    }

    protected Assessment getAssessment (UMLElement element) {
        return modelElementAssessmentMapping.get(element.getElementID());
    }

    protected void addAssessment (int elementID, Assessment assessment) {
        modelElementAssessmentMapping.putIfAbsent(elementID, assessment);
    }

    /**
     * Used for statistic
     */
    public Map<Integer, Assessment> getAssessmentsMap() {
        return this.modelElementAssessmentMapping;
    }
}
