package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;

public abstract class UMLDiagram {

    protected long modelSubmissionId;

    protected CompassResult lastAssessmentCompassResult = null;

    public UMLDiagram(long modelSubmissionId) {
        this.modelSubmissionId = modelSubmissionId;
    }

    public long getModelSubmissionId() {
        return modelSubmissionId;
    }

    public void setLastAssessmentCompassResult(CompassResult compassResult) {
        lastAssessmentCompassResult = compassResult;
    }

    public CompassResult getLastAssessmentCompassResult() {
        return lastAssessmentCompassResult;
    }
}
