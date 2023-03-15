import dayjs from 'dayjs/esm';

import { User } from 'app/core/user/user.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Result } from 'app/entities/result.model';
import { Team } from 'app/entities/team.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export enum ComplaintType {
    COMPLAINT = 'COMPLAINT',
    MORE_FEEDBACK = 'MORE_FEEDBACK',
}

export class Complaint implements BaseEntity {
    public id?: number;

    public complaintText?: string;
    public accepted?: boolean;
    public submittedTime?: dayjs.Dayjs;
    public result?: Result;
    public student?: User;
    public team?: Team;
    public complaintType?: ComplaintType;
    public complaintResponse?: ComplaintResponse;

    constructor() {}
}
