export enum Action {
    REFRESH_LOCK = 'REFRESH_LOCK',
    RESOLVE_COMPLAINT = 'RESOLVE_COMPLAINT',
}

export class ComplaintResponseUpdateDTO {
    public responseText?: string;
    public complaintIsAccepted?: boolean;
    public action?: Action;

    constructor(action: Action, responseText?: string, complaintIsAccepted?: boolean) {
        this.responseText = responseText;
        this.complaintIsAccepted = complaintIsAccepted;
        this.action = action;
    }
}
