package de.tum.in.www1.artemis.service.compass.umlmodel.activity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLActivityDiagram extends UMLDiagram implements Serializable {

    private List<UMLActivityNode> activityNodeList;

    private List<UMLActivity> activityList;

    private List<UMLControlFlow> controlFlowList;

    /**
     * to make mockito happy
     */
    public UMLActivityDiagram() {
        super();
    }

    public UMLActivityDiagram(long modelSubmissionId, List<UMLActivityNode> activityNodeList, List<UMLActivity> activityList, List<UMLControlFlow> controlFlowList) {
        super(modelSubmissionId);

        this.activityNodeList = activityNodeList;
        this.activityList = activityList;
        this.controlFlowList = controlFlowList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        for (UMLActivityNode activityNode : getActivityNodeList()) {
            if (activityNode.getJSONElementID().equals(jsonElementId)) {
                return activityNode;
            }
        }

        for (UMLActivity activity : getActivityList()) {
            if (activity.getJSONElementID().equals(jsonElementId)) {
                return activity;
            }
        }

        for (UMLControlFlow controlFlow : getControlFlowList()) {
            if (controlFlow.getJSONElementID().equals(jsonElementId)) {
                return controlFlow;
            }
        }

        return null;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(activityNodeList);
        modelElements.addAll(activityList);
        modelElements.addAll(controlFlowList);
        return modelElements;
    }

    /**
     * Get the list of activity nodes contained in this UML activity diagram.
     *
     * @return the list of UML activity nodes
     */
    public List<UMLActivityNode> getActivityNodeList() {
        return activityNodeList;
    }

    /**
     * Get the list of control flow elements contained in this UML activity diagram.
     *
     * @return the list of UML control flow elements
     */
    public List<UMLControlFlow> getControlFlowList() {
        return controlFlowList;
    }

    /**
     * Get the list of activities contained in this UML activity diagram.
     *
     * @return the list of UML activities
     */
    public List<UMLActivity> getActivityList() {
        return activityList;
    }
}
