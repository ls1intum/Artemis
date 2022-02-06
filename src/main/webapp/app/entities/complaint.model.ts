import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Result } from 'app/entities/result.model';
import { Team } from 'app/entities/team.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';

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

/**
 * Returns the time needed to evaluate the complaint. If it hasn't been evaluated yet, the difference between the submission time and now is used.
 * @param complaint for which the response time should be calculated
 * @return returns the passed time in seconds
 */
export function getResponseTimeInSeconds(complaint: Complaint): number {
    let responseTime;
    if (complaint.accepted !== undefined) {
        responseTime = complaint.complaintResponse?.submittedTime?.diff(complaint.submittedTime, 'seconds') || NaN;
    } else {
        responseTime = dayjs().diff(complaint.submittedTime, 'seconds');
    }
    return responseTime;
}

/**
 * Determines if the complaint should be highlighted. This is the case if the complaint hasn't been reviewed and was submitted more than one week ago.
 * @param complaint for which it should be determined if highlighting is needed
 * @return returns true iff the complaint should be highlighted
 */
export function shouldHighlightComplaint(complaint: Complaint): boolean {
    if (complaint.accepted !== undefined) {
        return false;
    }

    const complaintSubmittedTime = complaint.submittedTime;
    if (complaintSubmittedTime) {
        return dayjs().diff(complaintSubmittedTime, 'days') > 7;
    }

    return false;
}
