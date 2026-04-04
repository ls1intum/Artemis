import dayjs from 'dayjs/esm';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ResultDTO } from 'app/exercise/shared/entities/result/result.model';
import { ComplaintResponseDTO } from 'app/assessment/shared/entities/complaint-response-dto.model';

export class ComplaintDTO {
    public id: number;
    public complaintText?: string;
    public complaintIsAccepted?: boolean;
    public submittedTime?: dayjs.Dayjs;
    public complaintType?: ComplaintType;
    public complaintResponse?: ComplaintResponseDTO;
    public result?: ResultDTO;
    public participantId?: number;
}
