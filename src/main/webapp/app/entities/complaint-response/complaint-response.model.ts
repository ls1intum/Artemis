import { Moment } from 'moment';
import { Complaint } from '../complaint';
import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared';

export class ComplaintResponse implements BaseEntity {
    public id: number;

    public responseText: string;
    public submittedTime: Moment | null;
    public complaint: Complaint;
    public reviewer: User;

    constructor() {}
}
