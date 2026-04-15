import dayjs from 'dayjs/esm';
import { UserIdAndLoginDTO } from 'app/core/user/user.model';

export enum ComplaintAction {
    REFRESH_LOCK = 'REFRESH_LOCK',
    RESOLVE_COMPLAINT = 'RESOLVE_COMPLAINT',
}

export class ComplaintResponseUpdateDTO {
    public responseText?: string;
    public complaintIsAccepted?: boolean;
    public action?: ComplaintAction;
}

/**
 * DTO representing a complaint response returned by the server.
 */
export class ComplaintResponseDTO {
    public id: number;
    public responseText?: string;
    public submittedTime?: dayjs.Dayjs;
    public isCurrentlyLocked?: boolean;
    public lockEndDate?: dayjs.Dayjs;
    public complaintIsAccepted?: boolean;
    public complaintId: number;
    public reviewer?: UserIdAndLoginDTO;
}
