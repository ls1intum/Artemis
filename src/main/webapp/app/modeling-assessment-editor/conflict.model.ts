import { Result } from 'app/entities/result';
import { Moment } from 'moment';

export class Conflict {
    id: number;
    result: Result;
    modelElementId: string;
    state: EscalationState;
    creationDate: Moment;
    resolutionDate: Moment;
    conflictingResults: ConflictingResult[];

    constructor(id: number, result: Result, modelElementId: string, state: EscalationState, creationDate: Moment, resolutionDate: Moment, conflictingResults: ConflictingResult[]) {
        this.id = id;
        this.result = result;
        this.modelElementId = modelElementId;
        this.state = state;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.conflictingResults = conflictingResults;
    }
}

export class ConflictingResult {
    modelElementId: string;
    result: Result;

    constructor(modelElementId: string, result: Result) {
        this.modelElementId = modelElementId;
        this.result = result;
    }
}

export enum EscalationState {
    ON_REVIEW_BY_CAUSER,
    ON_REVIEW_BY_AFFECTED_TUTOR,
    ON_REVIEW_BY_INSTRUCTOR,
    RESOLVED,
}
