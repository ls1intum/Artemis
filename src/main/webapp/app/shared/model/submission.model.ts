export const enum SubmissionType {
    MANUAL = 'MANUAL',
    TIMEOUT = 'TIMEOUT'
}

export interface ISubmission {
    id?: number;
    submitted?: boolean;
    type?: SubmissionType;
}

export class Submission implements ISubmission {
    constructor(public id?: number, public submitted?: boolean, public type?: SubmissionType) {
        this.submitted = this.submitted || false;
    }
}
