package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.HashMap;

public class AssessmentIndex {

    private HashMap<Integer, Assessment> modelElementAssessmentMapping;

    public AssessmentIndex() {
        modelElementAssessmentMapping = new HashMap<>();
    }

    Assessment getAssessment (UMLElement element) {
        return modelElementAssessmentMapping.get(element.getElementID());
    }

    void addAssessment (int elementID, Assessment assessment) {
        modelElementAssessmentMapping.putIfAbsent(elementID, assessment);
    }
}
