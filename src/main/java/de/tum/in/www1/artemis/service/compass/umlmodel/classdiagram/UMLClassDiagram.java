package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLClassDiagram extends UMLDiagram implements Serializable {

    private final List<UMLClass> classList;

    private final List<UMLRelationship> relationshipList;

    private final List<UMLPackage> packageList;

    public UMLClassDiagram(long modelSubmissionId, List<UMLClass> classList, List<UMLRelationship> relationshipList, List<UMLPackage> packageList) {
        super(modelSubmissionId);

        this.classList = classList;
        this.relationshipList = relationshipList;
        this.packageList = packageList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        UMLElement element;

        for (UMLClass umlClass : getClassList()) {
            element = umlClass.getElementByJSONID(jsonElementId);
            if (element != null) {
                return element;
            }
        }

        for (UMLRelationship relationship : getRelationshipList()) {
            if (relationship.getJSONElementID().equals(jsonElementId)) {
                return relationship;
            }
        }

        for (UMLPackage umlPackage : getPackageList()) {
            if (umlPackage.getJSONElementID().equals(jsonElementId)) {
                return umlPackage;
            }
        }

        return null;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(classList);
        modelElements.addAll(relationshipList);
        modelElements.addAll(packageList);

        return modelElements;
    }

    @Override
    public List<UMLElement> getAllModelElements() {
        List<UMLElement> modelElements = super.getAllModelElements();

        for (UMLClass umlClass : classList) {
            modelElements.addAll(umlClass.getAttributes());
            modelElements.addAll(umlClass.getMethods());
        }

        return modelElements;
    }

    /**
     * Get the list of UML classes contained in this class diagram.
     *
     * @return the list of UML classes
     */
    public List<UMLClass> getClassList() {
        return classList;
    }

    /**
     * Get the list of UML relationships contained in this class diagram.
     *
     * @return the list of UML classes
     */
    public List<UMLRelationship> getRelationshipList() {
        return relationshipList;
    }

    /**
     * Get the list of UML packages contained in this class diagram.
     *
     * @return the list of UML classes
     */
    public List<UMLPackage> getPackageList() {
        return packageList;
    }
}
