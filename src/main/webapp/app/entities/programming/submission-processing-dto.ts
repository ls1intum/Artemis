import dayjs from 'dayjs/esm';

export class SubmissionProcessingDTO {
    public exerciseId?: number;
    public participationId?: number;
    public commitHash?: string;
    public submissionDate?: dayjs.Dayjs;
    public estimatedCompletionDate?: dayjs.Dayjs;
    public buildStartDate?: dayjs.Dayjs;
}
