package de.tum.in.www1.artemis.service.compass.umlmodel.communication;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.object.UMLObject;

public class UMLCommunicationDiagram extends UMLDiagram {

    private final List<UMLObject> objectList;

    private final List<UMLCommunicationLink> communicationLinkList;

    public UMLCommunicationDiagram(long modelSubmissionId, List<UMLObject> objectList, List<UMLCommunicationLink> communicationLinkList) {
        super(modelSubmissionId);
        this.objectList = objectList;
        this.communicationLinkList = communicationLinkList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLObject object : getObjectList()) {
            if (object.getJSONElementID().equals(jsonElementId)) {
                return object;
            }
        }

        for (UMLCommunicationLink communicationLink : getCommunicationLinkList()) {
            if (communicationLink.getJSONElementID().equals(jsonElementId)) {
                return communicationLink;
            }
        }

        return null;
    }

    public List<UMLObject> getObjectList() {
        return objectList;
    }

    public List<UMLCommunicationLink> getCommunicationLinkList() {
        return communicationLinkList;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(objectList);
        modelElements.addAll(communicationLinkList);
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

}
