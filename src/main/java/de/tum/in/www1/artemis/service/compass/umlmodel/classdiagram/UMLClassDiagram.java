package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.Collections;
import java.util.List;

public class UMLClassDiagram extends UMLDiagram {

    private final List<UMLPackage> packageList;

    private final List<UMLClass> classList;

    private final List<UMLRelationship> relationshipList;

    public UMLClassDiagram(long modelSubmissionId, List<UMLClass> classList, List<UMLRelationship> relationshipList, List<UMLPackage> packageList) {
        super(modelSubmissionId);
        this.packageList = packageList;
        this.classList = classList;
        this.relationshipList = relationshipList;
    }

    @Override
    protected double similarConnectableElementScore(UMLElement referenceElement) {
        return classList.stream().mapToDouble(connectableElement -> connectableElement.overallSimilarity(referenceElement)).max().orElse(0);
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        UMLElement element;

        for (UMLPackage umlPackage : packageList) {
            if (umlPackage.getJSONElementID().equals(jsonElementId)) {
                return umlPackage;
            }
        }

        for (UMLClass umlClass : classList) {
            element = umlClass.getElementByJSONID(jsonElementId);
            if (element != null) {
                return element;
            }
        }

        for (UMLRelationship relationship : relationshipList) {
            if (relationship.getJSONElementID().equals(jsonElementId)) {
                return relationship;
            }
        }

        return null;
    }

    @Override
    protected List<UMLElement> getConnectableElements() {
        return getClassList() != null ? Collections.unmodifiableList(getClassList()) : Collections.emptyList();
    }

    @Override
    protected List<UMLElement> getRelations() {
        return getRelationshipList() != null ? Collections.unmodifiableList(getRelationshipList()) : Collections.emptyList();
    }

    @Override
    protected List<UMLElement> getContainerElements() {
        return getPackageList() != null ? Collections.unmodifiableList(getPackageList()) : Collections.emptyList();
    }

    public List<UMLClass> getClassList() {
        return classList;
    }

    public List<UMLRelationship> getRelationshipList() {
        return relationshipList;
    }

    public List<UMLPackage> getPackageList() {
        return packageList;
    }
}
