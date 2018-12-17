import { Moment } from 'moment';
import { Complaint } from '../complaint';
import { User } from './../../core';
import { BaseEntity } from 'app/shared';

export class ComplaintResponse implements BaseEntity {
    public id: number;

    public responseText: string;
    public submittedTime: Moment;
    public complaint: Complaint;
    public reviewer: User;

    constructor() {}
}
