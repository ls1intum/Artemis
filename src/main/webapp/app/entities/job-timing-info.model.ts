import dayjs from 'dayjs/esm';

export class JobTimingInfo {
    public submissionDate?: dayjs.Dayjs;
    public buildStartDate?: dayjs.Dayjs;
    public buildCompletionDate?: dayjs.Dayjs;
    public buildDuration?: number;
}
