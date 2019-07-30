import { Result } from 'app/entities/result';
import { Moment } from 'moment';
import { Feedback } from 'app/entities/feedback';

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
    updatedFeedback: Feedback;

    static getReferencedFeedback(conflictingResult: ConflictingResult): Feedback | undefined {
        return conflictingResult.result.feedbacks.find(value => value.referenceId === conflictingResult.modelElementId);
    }

    constructor(modelElementId: string, result: Result, updatedFeedback: Feedback) {
        this.modelElementId = modelElementId;
        this.result = result;
        this.updatedFeedback = updatedFeedback;
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
