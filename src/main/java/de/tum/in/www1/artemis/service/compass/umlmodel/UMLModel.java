package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

import java.util.List;


public class UMLModel {

    private List<UMLPackage> packageList;
    private List<UMLClass> classList;
    private List<UMLAssociation> associationList;

    private long modelID;

    private CompassResult lastAssessmentCompassResult = null;


    public UMLModel() {
    }


    public UMLModel(List<UMLPackage> packageList, List<UMLClass> classList, List<UMLAssociation> associationList, long modelID) {
        this.packageList = packageList;
        this.classList = classList;
        this.associationList = associationList;
        this.modelID = modelID;
    }

    /**
     * Compare this with another model to calculate the similarity
     *
     * @param reference the uml model to compare with
     * @return the similarity as number [0-1]
     */
    public double similarity(UMLModel reference) {
        double sim1 = reference.similarityScore(this);
        double sim2 = this.similarityScore(reference);

        return sim1 * sim2;
    }

    private double similarityScore(UMLModel reference) {
        double similarity = 0;

        int elementCount = classList.size() + associationList.size();

        if (elementCount == 0) {
            return 0;
        }

        double weight = 1.0 / elementCount;

        int missingCount = 0;

        for (UMLClass UMLConnectableElement : classList) {
            double similarityValue = reference.similarConnectableElementScore(UMLConnectableElement);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        for (UMLAssociation umlAssociation : associationList) {
            double similarityValue = reference.similarUMLRelationScore(umlAssociation);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // Punish missing classes (on either side)
        int referenceMissingCount = Math.max(reference.classList.size() - classList.size(), 0);
        referenceMissingCount += Math.max(reference.associationList.size() - associationList.size(), 0);

        missingCount += referenceMissingCount;

        if (missingCount > 0 ) {
            double penaltyWeight = 1.0 / missingCount;
            similarity -= penaltyWeight * CompassConfiguration.MISSING_ELEMENT_PENALTY * missingCount;
        }


        if (similarity < 0) {
            similarity = 0;
        } else if (similarity > 1 && similarity < 1.000001) {
            similarity = 1;
        }

        return similarity;
    }

    private double similarConnectableElementScore(UMLClass referenceConnectable) {
        return classList.stream().mapToDouble(connectableElement ->
            connectableElement.overallSimilarity(referenceConnectable)).max().orElse(0);
    }

    private double similarUMLRelationScore(UMLAssociation referenceRelation) {
        return associationList.stream().mapToDouble(umlRelation ->
            umlRelation.similarity(referenceRelation)).max().orElse(0);
    }

    public String getName() {
        return "Model " + modelID;
    }

    public boolean isUnassessed () {
        return lastAssessmentCompassResult == null;
    }

    /**
     * check if all model elements have been assessed
     *
     * @return isEntirelyAssessed
     */
    public boolean isEntirelyAssessed () {
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

        for (UMLAssociation relation : associationList) {
            if (!lastAssessmentCompassResult.getJsonIdPointsMapping().containsKey(relation.getJSONElementID())) {
                return false;
            }
        }

        return true;
    }


    private int getModelElementCount() {
        return classList.stream().mapToInt(UMLClass::getElementCount).sum() + associationList.size();
    }

    @SuppressWarnings("unused")
    public double getLastAssessmentConfidence () {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentCompassResult.getConfidence();
    }

    public double getLastAssessmentCoverage () {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentCompassResult.getCoverage();
    }

    /**
     * Gets an UML element of the UML model with the given id.
     *
     * @param jsonElementId the id of the UML element
     * @return the UML element if one could be found for the given id, null otherwise
     */
    public UMLElement getElementByJSONID (String jsonElementId) {
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

        for (UMLAssociation umlAssociation : associationList) {
            if (umlAssociation.getJSONElementID().equals(jsonElementId)) {
                return umlAssociation;
            }
        }

        return null;
    }

    public long getModelID () {return modelID;}

    public void setLastAssessmentCompassResult(CompassResult compassResult) {
        lastAssessmentCompassResult = compassResult;
    }

    public CompassResult getLastAssessmentCompassResult() {
        return lastAssessmentCompassResult;
    }

    public List<UMLClass> getClassList() {
        return classList;
    }

    public List<UMLAssociation> getAssociationList() {
        return associationList;
    }

    /**
     * Checks if the model contains an element with the given elementId.
     *
     * @param jsonElementId the id of the UML element
     * @return true if the element was found, false otherwise
     */
    public boolean containsElement(String jsonElementId) {
        return getElementByJSONID(jsonElementId) != null;
    }
}
