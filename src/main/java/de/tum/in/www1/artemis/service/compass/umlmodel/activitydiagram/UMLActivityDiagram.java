package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;

public class UMLActivityDiagram extends UMLDiagram {

    private final List<UMLActivity> activities;

    private final List<UMLControlFlow> controlFlowElements;

    public UMLActivityDiagram(long modelSubmissionId, List<UMLActivity> activities, List<UMLControlFlow> controlFlowElements) {
        super(modelSubmissionId);
        this.activities = activities;
        this.controlFlowElements = controlFlowElements;
    }
}
