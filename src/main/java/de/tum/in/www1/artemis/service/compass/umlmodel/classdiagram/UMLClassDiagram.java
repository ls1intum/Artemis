package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

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
    protected double similarityScore(UMLDiagram reference) {
        // TODO: also check UMLPackage elements
        if (reference == null || reference.getClass() != UMLClassDiagram.class) {
            return 0;
        }

        UMLClassDiagram classDiagramReference = (UMLClassDiagram) reference;
        double similarity = 0;

        int elementCount = classList.size() + relationshipList.size();
        if (elementCount == 0) {
            return 0;
        }
        double weight = 1.0 / elementCount;

        int missingCount = 0;

        for (UMLClass connectableElement : classList) {
            double similarityValue = classDiagramReference.similarConnectableElementScore(connectableElement);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        for (UMLRelationship relationship : relationshipList) {
            double similarityValue = classDiagramReference.similarUMLRelationScore(relationship);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // Punish missing classes (on either side)
        int referenceMissingCount = Math.max(classDiagramReference.classList.size() - classList.size(), 0);
        referenceMissingCount += Math.max(classDiagramReference.relationshipList.size() - relationshipList.size(), 0);

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

    private double similarConnectableElementScore(UMLClass referenceConnectable) {
        return classList.stream().mapToDouble(connectableElement -> connectableElement.overallSimilarity(referenceConnectable)).max().orElse(0);
    }

    private double similarUMLRelationScore(UMLRelationship referenceRelation) {
        return relationshipList.stream().mapToDouble(umlRelation -> umlRelation.similarity(referenceRelation)).max().orElse(0);
    }

    /**
     * check if all model elements have been assessed
     *
     * @return isEntirelyAssessed
     */
    @SuppressWarnings("unused")
    public boolean isEntirelyAssessed() {
        if (isUnassessed() || lastAssessmentCompassResult.getCoverage() != 1) {
            return false;
        }

        // this model only contains unique elements
        if (lastAssessmentCompassResult.entitiesCovered() == getModelElementCount()) {
            return true;
        }

        for (UMLClass umlClass : classList) {
            if (!lastAssessmentCompassResult.getJsonIdPointsMapping().containsKey(umlClass.getJSONElementID())) {
                return false;
            }

            for (UMLAttribute attribute : umlClass.getAttributes()) {
                if (!lastAssessmentCompassResult.getJsonIdPointsMapping().containsKey(attribute.getJSONElementID())) {
                    return false;
                }
            }

            for (UMLMethod method : umlClass.getMethods()) {
                if (!lastAssessmentCompassResult.getJsonIdPointsMapping().containsKey(method.getJSONElementID())) {
                    return false;
                }
            }
        }

        for (UMLRelationship relation : relationshipList) {
            if (!lastAssessmentCompassResult.getJsonIdPointsMapping().containsKey(relation.getJSONElementID())) {
                return false;
            }
        }

        return true;
    }

    private int getModelElementCount() {
        return classList.stream().mapToInt(UMLClass::getElementCount).sum() + relationshipList.size();
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
