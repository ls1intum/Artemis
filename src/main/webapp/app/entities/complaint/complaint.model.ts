import { Moment } from 'moment';
import { Result } from '../result';
import { User } from './../../core';
import { BaseEntity } from 'app/shared';

export enum ComplaintType {
    COMPLAINT = 'COMPLAINT',
    MORE_FEEDBACK = 'MORE_FEEDBACK',
}

export class Complaint implements BaseEntity {
    public id: number;

    public complaintText: string;
    public accepted: boolean;
    public submittedTime: Moment;
    public resultBeforeComplaint: string;
    public result: Result;
    public student: User;
    public complaintType: ComplaintType;

    constructor() {}
}
