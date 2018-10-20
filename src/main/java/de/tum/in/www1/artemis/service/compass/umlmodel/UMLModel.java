package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.Result;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

import java.util.List;


public class UMLModel {

    private List<UMLClass> connectableList;
    private List<UMLRelation> relationList;

    private long modelID;

    private Result lastAssessmentResult = null;

    public UMLModel(List<UMLClass> connectableList, List<UMLRelation> relationList, long modelID) {
        this.connectableList = connectableList;
        this.relationList = relationList;
        this.modelID = modelID;
    }

    public double similarity(UMLModel reference) {
        double sim1 = reference.similarityScore(this);
        double sim2 = this.similarityScore(reference);

        return sim1 * sim2;
    }

    public double similarityScore(UMLModel reference) {
        double similarity = 0;

        int elementCount = connectableList.size() + relationList.size();

        if (elementCount == 0) {
            return 0;
        }

        double weight = 1.0 / elementCount;

        int missingCount = 0;

        for (UMLClass UMLConnectableElement : connectableList) {
            double similarityValue = reference.similarConnectableElementScore(UMLConnectableElement);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        for (UMLRelation umlRelation :relationList) {
            double similarityValue = reference.similarUMLRelationScore(umlRelation);
            similarity += weight * similarityValue;

            // = no match found
            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // Punish missing classes (on either side)
        int referenceMissingCount = Math.max(reference.connectableList.size() - connectableList.size(), 0);
        referenceMissingCount += Math.max(reference.relationList.size() - relationList.size(), 0);

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
        return connectableList.stream().mapToDouble(connectableElement ->
            connectableElement.overallSimilarity(referenceConnectable)).max().orElse(0);
    }

    private double similarUMLRelationScore(UMLRelation referenceRelation) {
        return relationList.stream().mapToDouble(umlRelation ->
            umlRelation.similarity(referenceRelation)).max().orElse(0);
    }


    // <editor-fold desc="getter">


    public String getName() {
        return "Model " + modelID;
    }

    public boolean isUnassessed () {
        return lastAssessmentResult == null;
    }

    public boolean isEntirelyAssessed () {
        if (isUnassessed() || lastAssessmentResult.getCoverage() != 1) {
            return false;
        }

        // this model only contains unique elements
        if (lastAssessmentResult.entitiesCovered() == getModelElementCount()) {
            return true;
        }

        for (UMLClass umlClass : connectableList) {
            if (!lastAssessmentResult.getJsonIdPointsMapping().containsKey(umlClass.jsonElementID)) {
                return false;
            }

            for (UMLAttribute attribute : umlClass.attributeList) {
                if (!lastAssessmentResult.getJsonIdPointsMapping().containsKey(attribute.jsonElementID)) {
                    return false;
                }
            }

            for (UMLMethod method : umlClass.methodList) {
                if (!lastAssessmentResult.getJsonIdPointsMapping().containsKey(method.jsonElementID)) {
                    return false;
                }
            }
        }

        for (UMLRelation relation : relationList) {
            if (!lastAssessmentResult.getJsonIdPointsMapping().containsKey(relation.jsonElementID)) {
                return false;
            }
        }

        return true;
    }


    private int getModelElementCount() {
        return connectableList.stream().mapToInt(UMLClass::getElementCount).sum() + relationList.size();
    }

    public double getLastAssessmentConfidence () {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentResult.getConfidence();
    }

    public double getLastAssessmentCoverage () {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentResult.getCoverage();
    }

    public UMLElement getElementByJSONID (String jsonID) {
        UMLElement element;

        for (UMLClass UMLConnectableElement : connectableList) {
            element = UMLConnectableElement.getElementByJSONID(jsonID);

            if (element != null) {
                return element;
            }
        }

        for (UMLRelation umlRelation : relationList) {
            if (umlRelation.getJSONElementID().equals(jsonID)) {
                return umlRelation;
            }
        }

        return null;
    }

    public long getModelID () {return modelID;}

    public void setLastAssessmentResult(Result result) {
        lastAssessmentResult = result;
    }

    public Result getLastAssessmentResult() {
        return lastAssessmentResult;
    }

    public List<UMLClass> getConnectableList() {
        return connectableList;
    }

    public List<UMLRelation> getRelationList() {
        return relationList;
    }

    // </editor-fold>
}
