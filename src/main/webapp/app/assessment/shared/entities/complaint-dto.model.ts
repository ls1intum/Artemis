import dayjs from 'dayjs/esm';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';

export class ComplaintDTO {
    public id: number;
    public complaintText?: string;
    public complaintIsAccepted?: boolean;
    public submittedTime?: dayjs.Dayjs;
    public complaintType?: ComplaintType;
    public complaintResponseId?: number;
    public participantId?: number;
}
