package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

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
    protected double similarityScore(UMLDiagram reference) {
        // TODO: also check UMLActivity elements
        if (reference == null || reference.getClass() != UMLActivityDiagram.class) {
            return 0;
        }

        UMLActivityDiagram activityDiagramReference = (UMLActivityDiagram) reference;

        double similarity = 0;

        int elementCount = activityNodeList.size() + controlFlowList.size();
        if (elementCount == 0) {
            return 0;
        }
        double weight = 1.0 / elementCount;

        int missingCount = 0;

        for (UMLActivityNode activityNode : activityNodeList) {
            double similarityValue = activityDiagramReference.similarConnectableElementScore(activityNode);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        for (UMLControlFlow controlFlow : controlFlowList) {
            double similarityValue = activityDiagramReference.similarUMLRelationScore(controlFlow);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // Punish missing classes (on either side)
        int referenceMissingCount = Math.max(activityDiagramReference.activityNodeList.size() - activityNodeList.size(), 0);
        referenceMissingCount += Math.max(activityDiagramReference.controlFlowList.size() - controlFlowList.size(), 0);

        missingCount += referenceMissingCount;

        if (missingCount > 0) {
            // TODO: the two lines below are equal to "similarity -= CompassConfiguration.MISSING_ELEMENT_PENALTY;"
            double penaltyWeight = 1.0 / missingCount;
            similarity -= penaltyWeight * CompassConfiguration.MISSING_ELEMENT_PENALTY * missingCount;
        }

        if (similarity < 0) {
            similarity = 0;
        }
        else if (similarity > 1 && similarity < 1.000001) {
            similarity = 1;
        }

        return similarity;
    }

    private double similarConnectableElementScore(UMLActivityNode referenceConnectable) {
        return activityNodeList.stream().mapToDouble(connectableElement -> connectableElement.similarity(referenceConnectable)).max().orElse(0);
    }

    private double similarUMLRelationScore(UMLControlFlow referenceRelation) {
        return controlFlowList.stream().mapToDouble(umlRelation -> umlRelation.similarity(referenceRelation)).max().orElse(0);
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
