import dayjs from 'dayjs/esm';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ResultSimpleDTO } from 'app/exercise/shared/entities/result/result.model';
import { ComplaintResponseDTO } from 'app/assessment/shared/entities/complaint-response-dto.model';

export class ComplaintDTO {
    public id?: number;
    public complaintText?: string;
    public submittedTime?: dayjs.Dayjs;
    public complaintType?: ComplaintType;
    public complaintIsAccepted?: boolean;
    public complaintResponse?: ComplaintResponseDTO;
    public result?: ResultSimpleDTO;
}
