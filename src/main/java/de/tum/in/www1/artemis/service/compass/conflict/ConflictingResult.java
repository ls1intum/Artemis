package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.Result;

public class ConflictingResult {

    private String modelElementId;

    private Result result;

    public String getModelElementId() {
        return modelElementId;
    }

    public Result getResult() {
        return result;
    }

    public ConflictingResult() {
    }

    public ConflictingResult(String modelElementId, Result result) {
        this.modelElementId = modelElementId;
        this.result = result;
    }
}
