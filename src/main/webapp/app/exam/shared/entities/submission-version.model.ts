import { BaseEntity } from 'app/foundation/model/base-entity';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import dayjs from 'dayjs/esm';

/**
 * Deserialized from server data (fields assigned after construction, hence the definite-assignment markers).
 * Kept as a class rather than an interface because it is referenced as a value in a template
 * (`readonly SubmissionVersion = SubmissionVersion` in student-exam-timeline.component.ts).
 */
export class SubmissionVersion implements BaseEntity {
    public id?: number;
    public submission!: Submission;
    public createdDate!: dayjs.Dayjs;
    public content!: string;
}
