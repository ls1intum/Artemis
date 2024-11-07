import dayjs from 'dayjs/esm';

export class SubmissionProcessingDTO {
    public exerciseId?: number;
    public participationId?: number;
    public commitHash?: string;
    public estimatedCompletionDate?: dayjs.Dayjs;
}
