package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.deployment;

import java.util.List;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.component.UMLComponent;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.component.UMLComponentDiagram;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.component.UMLComponentInterface;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.component.UMLComponentRelationship;

public class UMLDeploymentDiagram extends UMLComponentDiagram {

    private final List<UMLNode> nodeList;

    private final List<UMLArtifact> artifactList;

    public UMLDeploymentDiagram(long modelSubmissionId, List<UMLComponent> componentList, List<UMLComponentInterface> componentInterfaceList,
            List<UMLComponentRelationship> componentRelationshipList, List<UMLNode> nodeList, List<UMLArtifact> artifactList) {
        super(modelSubmissionId, componentList, componentInterfaceList, componentRelationshipList);
        this.nodeList = nodeList;
        this.artifactList = artifactList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        var element = super.getElementByJSONID(jsonElementId);
        if (element != null) {
            return element;
        }

        for (UMLNode node : getNodeList()) {
            if (node.getJSONElementID().equals(jsonElementId)) {
                return node;
            }
        }

        for (UMLArtifact artifact : getArtifactList()) {
            if (artifact.getJSONElementID().equals(jsonElementId)) {
                return artifact;
            }
        }

        return null;
    }

    public List<UMLNode> getNodeList() {
        return nodeList;
    }

    public List<UMLArtifact> getArtifactList() {
        return artifactList;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = super.getModelElements();
        modelElements.addAll(nodeList);
        modelElements.addAll(artifactList);
        modelElements.addAll(getComponentRelationshipList());
        return modelElements;
    }
}
