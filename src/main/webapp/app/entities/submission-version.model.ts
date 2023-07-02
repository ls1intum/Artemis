import { BaseEntity } from 'app/shared/model/base-entity';
import { Submission } from 'app/entities/submission.model';
import dayjs from 'dayjs/esm';

export class SubmissionVersion implements BaseEntity {
    public id?: number;
    public submission: Submission;
    public createdDate: dayjs.Dayjs;
    public content: string;
}
