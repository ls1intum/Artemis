import dayjs from 'dayjs/esm';

export enum ComplaintAction {
    REFRESH_LOCK = 'REFRESH_LOCK',
    RESOLVE_COMPLAINT = 'RESOLVE_COMPLAINT',
}

export class ComplaintResponseUpdateDTO {
    public responseText?: string;
    public complaintIsAccepted?: boolean;
    public action?: ComplaintAction;
}

export class ComplaintResponseDTO {
    public id: number;
    public responseText?: string;
    public submittedTime?: dayjs.Dayjs;
    public isCurrentlyLocked?: boolean;
    public lockEndDate?: dayjs.Dayjs;
    public complaintIsAccepted?: boolean;
    public complaintId: number;
    public reviewerLogin: string;
}
