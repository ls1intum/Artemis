import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';

export class ComplaintRequestDTO {
    public resultId?: number;
    public complaintText?: string;
    public complaintType?: ComplaintType;
    public examId?: number;
}
