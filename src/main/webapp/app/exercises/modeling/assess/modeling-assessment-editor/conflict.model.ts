import { Result } from 'app/entities/result.model';
import { Moment } from 'moment';

export class Conflict {
    id: number;
    causingConflictingResult: ConflictingResult;
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

/**
 * Describes the state of a Conflict
 *
 * @type {{UNHANDLED: number, ESCALATED: number, RESOLVED: number}}
 */
export enum ConflictResolutionState {
    UNHANDLED,
    ESCALATED,
    RESOLVED,
}
