import { Moment } from 'moment';
import { IExerciseResult } from 'app/shared/model//exercise-result.model';
import { IParticipation } from 'app/shared/model//participation.model';

export const enum SubmissionType {
    MANUAL = 'MANUAL',
    TIMEOUT = 'TIMEOUT'
}

export interface ISubmission {
    id?: number;
    submitted?: boolean;
    submissionDate?: Moment;
    type?: SubmissionType;
    result?: IExerciseResult;
    participation?: IParticipation;
}

export class Submission implements ISubmission {
    constructor(
        public id?: number,
        public submitted?: boolean,
        public submissionDate?: Moment,
        public type?: SubmissionType,
        public result?: IExerciseResult,
        public participation?: IParticipation
    ) {
        this.submitted = this.submitted || false;
    }
}
