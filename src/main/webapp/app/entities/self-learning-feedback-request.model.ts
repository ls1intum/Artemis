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

    public participation?: Participation;
    public result?: Result;
    public submission?: Submission;

    public static isSelfLearningFeedbackRequest(obj: any): obj is SelfLearningFeedbackRequest {
        return obj instanceof SelfLearningFeedbackRequest;
    }
}
