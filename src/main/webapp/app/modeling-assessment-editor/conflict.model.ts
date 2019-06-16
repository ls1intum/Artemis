import { Result } from 'app/entities/result';
import { Moment } from 'moment';

export class Conflict {
    id: number;
    causingConflictingResult: ConflictingResult;
    state: EscalationState;
    creationDate: Moment;
    resolutionDate: Moment;
    resultsInConflict: ConflictingResult[];
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
    UNHANDLED,
    ESCALATED_TO_TUTORS_IN_CONFLICT,
    ESCALATED_TO_INSTRUCTOR,
    RESOLVED_BY_CAUSER,
    RESOLVED_BY_OTHER_TUTORS,
    RESOLVED_BY_INSTRUCTOR,
}
