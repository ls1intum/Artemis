package de.tum.in.www1.artemis.service.compass.conflict;

import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Result;

public class Conflict {

    private Long id;

    private Result result;

    private String modelElementId;

    private EscalationState state;

    private ZonedDateTime creationDate;

    private ZonedDateTime resolutionDate;

    private Set<ConflictingResult> conflictingResults;

    public Conflict() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public String getModelElementId() {
        return modelElementId;
    }

    public void setModelElementId(String modelElementId) {
        this.modelElementId = modelElementId;
    }

    public EscalationState getState() {
        return state;
    }

    public void setState(EscalationState state) {
        this.state = state;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public ZonedDateTime getResolutionDate() {
        return resolutionDate;
    }

    public void setResolutionDate(ZonedDateTime resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public Set<ConflictingResult> getConflictingResults() {
        return conflictingResults;
    }

    public void setConflictingResults(Set<ConflictingResult> conflictingResults) {
        this.conflictingResults = conflictingResults;
    }
}
