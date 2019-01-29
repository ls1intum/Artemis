package de.tum.in.www1.artemis.service.compass.conflict;

import de.tum.in.www1.artemis.domain.Result;

public class ConflictResultWrapper {

    private Conflict conflict;
    private Result result;


    public ConflictResultWrapper(Conflict conflict, Result result) {
        this.conflict = conflict;
        this.result = result;
    }


    public Conflict getConflict() {
        return conflict;
    }


    public void setConflict(Conflict conflict) {
        this.conflict = conflict;
    }


    public Result getResult() {
        return result;
    }


    public void setResult(Result result) {
        this.result = result;
    }
}
