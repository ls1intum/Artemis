import { BaseEntity } from './../../shared';
import { Result } from '../result';
import { Participation } from '../participation';

export const enum SubmissionType {
    MANUAL = 'MANUAL',
    TIMEOUT = 'TIMEOUT'
}

export abstract class Submission implements BaseEntity {

    public id: number;
    public submitted = false;   // default value
    public submissionDate: any;
    public type: SubmissionType;
    public result: Result;
    public participation: Participation;

    constructor() {
    }
}
