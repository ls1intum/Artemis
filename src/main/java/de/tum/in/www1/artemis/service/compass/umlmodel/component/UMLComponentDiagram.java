package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLCommunicationLink;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLObject;

public class UMLComponentDiagram extends UMLDiagram {

    // TODO recreate for the purpose of UML Component diagrams

    private final List<UMLObject> objectList;

    private final List<UMLCommunicationLink> communicationLinkList;

    public UMLComponentDiagram(long modelSubmissionId, List<UMLObject> objectList, List<UMLCommunicationLink> communicationLinkList) {
        super(modelSubmissionId);
        this.objectList = objectList;
        this.communicationLinkList = communicationLinkList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLObject object : objectList) {
            if (object.getJSONElementID().equals(jsonElementId)) {
                return object;
            }
        }

        for (UMLCommunicationLink communicationLink : communicationLinkList) {
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
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(objectList);
        modelElements.addAll(communicationLinkList);
        return modelElements;
    }
}
