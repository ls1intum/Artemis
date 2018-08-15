import { BaseEntity } from './../../shared';
import { Result } from '../result';
import { Participation } from '../participation';

export const enum SubmissionType {
    'MANUAL',
    'TIMEOUT'
}

export class Submission implements BaseEntity {
    constructor(
        public id?: number,
        public submitted?: boolean,
        public submissionDate?: any,
        public type?: SubmissionType,
        public result?: Result,
        public participation?: Participation,
    ) {
        this.submitted = false;
    }
}
