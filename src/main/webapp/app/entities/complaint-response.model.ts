import { Moment } from 'moment';
import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Complaint } from 'app/entities/complaint.model';

export class ComplaintResponse implements BaseEntity {
    public id?: number;

    public responseText?: string;
    public submittedTime?: Moment;
    public complaint?: Complaint;
    public reviewer?: User;

    constructor() {}
}
