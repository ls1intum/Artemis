import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Participation } from 'app/entities/participation/participation.model';
import { Submission } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';

export class SelfLearningFeedbackRequest implements BaseEntity {
    public id?: number;
    public requestDateTime?: dayjs.Dayjs;
    public responseDateTime?: dayjs.Dayjs;
    public successful?: boolean;

    public submission?: Submission;
    public result?: Result;
    public participation?: Participation;

    public static SelfLearningFeedbackRequest(obj: any): obj is Result {
        return obj.type === 'Result';
    }
}
