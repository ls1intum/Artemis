export enum ComplaintType {
    COMPLAINT = 'COMPLAINT',
    MORE_FEEDBACK = 'MORE_FEEDBACK',
}
export class ComplaintRequestDTO {
    public resultId?: number;
    public complaintText?: string;
    public complaintType?: ComplaintType;
    public examId?: number;

    constructor() {}
}
