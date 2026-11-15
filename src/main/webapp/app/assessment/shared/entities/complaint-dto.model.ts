import dayjs from 'dayjs/esm';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ResultSimpleDTO } from 'app/exercise/shared/entities/result/result.model';
import { ComplaintResponseDTO } from 'app/assessment/shared/entities/complaint-response-dto.model';

/**
 * DTO representing a complaint returned by the server.
 */
export class ComplaintDTO {
    public id?: number;
    public complaintText?: string;
    public submittedTime?: dayjs.Dayjs;
    public complaintType?: ComplaintType;
    public complaintIsAccepted?: boolean;
    public complaintResponse?: ComplaintResponseDTO;
    public result?: ResultSimpleDTO;
    public participant?: ParticipantDTO;
    /** Privacy-safe stable assessor id for tutor All-scope filtering (not login). */
    public assessorKey?: string;
    /** Privacy-safe assessor display name (first/last only; no login). */
    public assessorLabel?: string;
}

/**
 * DTO representing a participant returned by the server.
 */
export class ParticipantDTO {
    public id?: number;
    public name?: string;
    public login?: string;
    public isStudent?: boolean;
}
