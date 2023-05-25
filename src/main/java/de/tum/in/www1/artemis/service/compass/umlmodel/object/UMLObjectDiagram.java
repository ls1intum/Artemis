package de.tum.in.www1.artemis.service.compass.umlmodel.object;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLObjectDiagram extends UMLDiagram {

    private final List<UMLObject> objectList;

    private final List<UMLObjectLink> objectLinkList;

    public UMLObjectDiagram(long modelSubmissionId, List<UMLObject> objectList, List<UMLObjectLink> objectLinkList) {
        super(modelSubmissionId);
        this.objectList = objectList;
        this.objectLinkList = objectLinkList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLObject object : getObjectList()) {
            if (object.getJSONElementID().equals(jsonElementId)) {
                return object;
            }
        }

        for (UMLObjectLink objectLink : getObjectLinkList()) {
            if (objectLink.getJSONElementID().equals(jsonElementId)) {
                return objectLink;
            }
        }

        return null;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(objectList);
        modelElements.addAll(objectLinkList);
        return modelElements;
    }

    @Override
    public List<UMLElement> getAllModelElements() {
        List<UMLElement> modelElements = super.getAllModelElements();

        for (UMLObject umlObject : objectList) {
            modelElements.addAll(umlObject.getAttributes());
            modelElements.addAll(umlObject.getMethods());
        }

        return modelElements;
    }

    public List<UMLObject> getObjectList() {
        return objectList;
    }

    public List<UMLObjectLink> getObjectLinkList() {
        return objectLinkList;
    }
}
