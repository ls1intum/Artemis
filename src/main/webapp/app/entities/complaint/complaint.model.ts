import { Moment } from 'moment';
import { Result } from '../result';
import { User } from './../../core';
import { BaseEntity } from 'app/shared';

export class Complaint implements BaseEntity {
    public id: number;

    public complaintText: string;
    public accepted: boolean;
    public submittedTime: Moment;
    public resultBeforeComplaint: string;
    public result: Result;
    public student: User;

    constructor() {}
}
