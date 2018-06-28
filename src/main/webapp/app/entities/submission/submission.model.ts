import { BaseEntity } from './../../shared';

export const enum SubmissionType {
    'MANUAL',
    'TIMEOUT'
}

export class Submission implements BaseEntity {
    constructor(
        public id?: number,
        public submitted?: boolean,
        public type?: SubmissionType,
    ) {
        this.submitted = false;
    }
}
