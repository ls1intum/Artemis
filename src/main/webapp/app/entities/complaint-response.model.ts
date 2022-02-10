import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Complaint } from 'app/entities/complaint.model';

export class ComplaintResponse implements BaseEntity {
    public id?: number;

    public responseText?: string;
    public submittedTime?: dayjs.Dayjs;
    public complaint?: Complaint;
    public reviewer?: User;
    // transient property that will be calculated on the server
    public isCurrentlyLocked?: boolean;
    // transient property that will be calculated on the server
    public lockEndDate?: dayjs.Dayjs;

    constructor() {}
}
