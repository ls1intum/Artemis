export enum Action {
    REFRESH_LOCK = 'REFRESH_LOCK',
    RESOLVE_COMPLAINT = 'RESOLVE_COMPLAINT',
}

export class ComplaintResponseUpdateDTO {
    public responseText?: string;
    public accepted?: boolean;
    public action?: Action;

    constructor(action: Action, responseText?: string, accepted?: boolean) {
        this.responseText = responseText;
        this.accepted = accepted;
        this.action = action;
    }
}
