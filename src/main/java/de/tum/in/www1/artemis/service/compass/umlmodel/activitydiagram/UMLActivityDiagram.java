package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.Collections;
import java.util.List;

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
        for (UMLActivityNode activityNode : activityNodeList) {
            if (activityNode.getJSONElementID().equals(jsonElementId)) {
                return activityNode;
            }
        }

        for (UMLActivity activity : activityList) {
            if (activity.getJSONElementID().equals(jsonElementId)) {
                return activity;
            }
        }

        for (UMLControlFlow controlFlow : controlFlowList) {
            if (controlFlow.getJSONElementID().equals(jsonElementId)) {
                return controlFlow;
            }
        }

        return null;
    }

    @Override
    protected List<UMLElement> getConnectableElements() {
        return getActivityNodeList() != null ? Collections.unmodifiableList(getActivityNodeList()) : Collections.emptyList();
    }

    @Override
    protected List<UMLElement> getRelations() {
        return getControlFlowList() != null ? Collections.unmodifiableList(getControlFlowList()) : Collections.emptyList();
    }

    @Override
    protected List<UMLElement> getContainerElements() {
        return getActivityList() != null ? Collections.unmodifiableList(getActivityList()) : Collections.emptyList();
    }

    public List<UMLActivityNode> getActivityNodeList() {
        return activityNodeList;
    }

    public List<UMLControlFlow> getControlFlowList() {
        return controlFlowList;
    }

    public List<UMLActivity> getActivityList() {
        return activityList;
    }
}
