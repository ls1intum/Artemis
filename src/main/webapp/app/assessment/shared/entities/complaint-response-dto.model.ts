import dayjs from 'dayjs/esm';
import { UserPublicInfoDTO } from 'app/account/user/user.model';

export enum ComplaintAction {
    REFRESH_LOCK = 'REFRESH_LOCK',
    RESOLVE_COMPLAINT = 'RESOLVE_COMPLAINT',
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ComplaintResponseUpdateDTO {
    public responseText?: string;
    public complaintIsAccepted?: boolean;
    public action?: ComplaintAction;
}

/**
 * DTO representing a complaint response returned by the server.
 */
export interface ComplaintResponseDTO {
    id: number;
    responseText?: string;
    submittedTime?: dayjs.Dayjs;
    isCurrentlyLocked?: boolean;
    lockEndDate?: dayjs.Dayjs;
    complaintIsAccepted?: boolean;
    complaintId: number;
    reviewer?: UserPublicInfoDTO;
}
