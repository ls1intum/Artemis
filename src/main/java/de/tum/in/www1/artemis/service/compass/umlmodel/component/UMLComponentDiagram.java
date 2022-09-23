package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLComponentDiagram extends UMLDiagram {

    private final List<UMLComponent> componentList;

    private final List<UMLComponentInterface> componentInterfaceList;

    private final List<UMLComponentRelationship> componentRelationshipList;

    public UMLComponentDiagram(long modelSubmissionId, List<UMLComponent> componentList, List<UMLComponentInterface> componentInterfaceList,
            List<UMLComponentRelationship> componentRelationshipList) {
        super(modelSubmissionId);
        this.componentList = componentList;
        this.componentInterfaceList = componentInterfaceList;
        this.componentRelationshipList = componentRelationshipList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLComponent component : getComponentList()) {
            if (component.getJSONElementID().equals(jsonElementId)) {
                return component;
            }
        }

        for (UMLComponentInterface componentInterface : getComponentInterfaceList()) {
            if (componentInterface.getJSONElementID().equals(jsonElementId)) {
                return componentInterface;
            }
        }

        for (UMLComponentRelationship componentRelationship : getComponentRelationshipList()) {
            if (componentRelationship.getJSONElementID().equals(jsonElementId)) {
                return componentRelationship;
            }
        }

        return null;
    }

    public List<UMLComponent> getComponentList() {
        return componentList;
    }

    public List<UMLComponentInterface> getComponentInterfaceList() {
        return componentInterfaceList;
    }

    public List<UMLComponentRelationship> getComponentRelationshipList() {
        return componentRelationshipList;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(componentList);
        modelElements.addAll(componentInterfaceList);
        modelElements.addAll(componentRelationshipList);
        return modelElements;
    }
}
