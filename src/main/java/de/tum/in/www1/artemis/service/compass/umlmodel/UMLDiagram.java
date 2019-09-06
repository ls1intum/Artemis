package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;

public abstract class UMLDiagram {

    private long modelSubmissionId;

    protected CompassResult lastAssessmentCompassResult = null;

    public UMLDiagram(long modelSubmissionId) {
        this.modelSubmissionId = modelSubmissionId;
    }

    /**
     * Gets an UML element of the UML model with the given id.
     *
     * @param jsonElementId the id of the UML element
     * @return the UML element if one could be found for the given id, null otherwise
     */
    public abstract UMLElement getElementByJSONID(String jsonElementId);

    /**
     * Compare this with another diagram to calculate the similarity.
     *
     * @param reference the UML diagram to compare with
     * @return the similarity of the diagrams as number [0-1]
     */
    public abstract double similarity(UMLDiagram reference);

    public long getModelSubmissionId() {
        return modelSubmissionId;
    }

    public void setLastAssessmentCompassResult(CompassResult compassResult) {
        lastAssessmentCompassResult = compassResult;
    }

    public CompassResult getLastAssessmentCompassResult() {
        return lastAssessmentCompassResult;
    }

    public boolean isUnassessed() {
        return lastAssessmentCompassResult == null;
    }

    /**
     * Get the confidence of the last assessed compass result
     *
     * @return The confidence of the last compass result
     */
    public double getLastAssessmentConfidence() {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentCompassResult.getConfidence();
    }

    /**
     * Get the coverage for the last assessed compass result
     *
     * @return The coverage of the last compass results
     */
    public double getLastAssessmentCoverage() {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentCompassResult.getCoverage();
    }

    public String getName() {
        return "Model " + modelSubmissionId;
    }
}
