import dayjs from 'dayjs/esm';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ExamInformationDTO {
    public latestIndividualEndDate!: dayjs.Dayjs;
}
