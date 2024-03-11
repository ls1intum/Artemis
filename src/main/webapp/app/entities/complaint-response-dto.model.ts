export enum Action {
    REFRESH_LOCK = 'REFRESH_LOCK',
    RESOLVE_COMPLAINT = 'RESOLVE_COMPLAINT',
}

export class ComplaintResponseUpdateDTO {
    public complaintId?: number;

    public responseText?: string;
    public accepted?: boolean;
    public action?: Action;

    constructor(complaintId: number | undefined, action: Action, responseText?: string, accepted?: boolean) {
        this.complaintId = complaintId;
        this.responseText = responseText;
        this.accepted = accepted;
        this.action = action;
    }
}
