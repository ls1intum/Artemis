package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLActivityDiagram extends UMLDiagram {

    private final List<UMLActivityNode> activityNodeList;

    private final List<UMLActivity> activityList;

    private final List<UMLControlFlow> controlFlowList;

    public UMLActivityDiagram(long modelSubmissionId, List<UMLActivityNode> activityNodeList, List<UMLActivity> activityList, List<UMLControlFlow> controlFlowList) {
        super(modelSubmissionId);
        this.activityNodeList = activityNodeList;
        this.activityList = activityList;
        this.controlFlowList = controlFlowList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        // TODO: implement
        return null;
    }

    @Override
    public double similarity(UMLDiagram reference) {
        // TODO: implement
        return 0;
    }

    public List<UMLActivityNode> getActivityNodeList() {
        return activityNodeList;
    }

    public List<UMLActivity> getActivityList() {
        return activityList;
    }

    public List<UMLControlFlow> getControlFlowList() {
        return controlFlowList;
    }
}
