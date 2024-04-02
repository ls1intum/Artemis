export enum ComplaintType {
    COMPLAINT = 'COMPLAINT',
    MORE_FEEDBACK = 'MORE_FEEDBACK',
}
export class ComplaintRequestDTO {
    public resultId?: number;
    public complaintText?: string;
    public complaintType?: ComplaintType;
    public examId?: number;

    constructor(resultId: number, complaintType: ComplaintType, complaintText?: string, examId?: number) {
        this.resultId = resultId;
        this.complaintText = complaintText;
        this.complaintType = complaintType;
        this.examId = examId;
    }
}
