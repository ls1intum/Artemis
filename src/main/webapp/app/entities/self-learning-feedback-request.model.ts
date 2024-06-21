import type { BaseEntity } from 'app/shared/model/base-entity';
import type dayjs from 'dayjs/esm';
import type { Submission } from 'app/entities/submission.model';
import type { Result } from 'app/entities/result.model';

export class SelfLearningFeedbackRequest implements BaseEntity {
    public id?: number;
    public requestDateTime?: dayjs.Dayjs;
    public responseDateTime?: dayjs.Dayjs;
    public successful?: boolean;
    public participationId?: number;

    public result?: Result;
    public submission?: Submission;

    // parsed objects from the server do not have a prototype by default, the method will not work
    // you need to explicitly convert objects in order for this function to work
    public static isSelfLearningFeedbackRequest(obj: unknown): obj is SelfLearningFeedbackRequest {
        return obj instanceof SelfLearningFeedbackRequest;
    }

    public static isPending(selfLearningFeedbackRequest: SelfLearningFeedbackRequest): boolean {
        return selfLearningFeedbackRequest.requestDateTime !== undefined && selfLearningFeedbackRequest.successful === undefined;
    }

    public static isFailed(selfLearningFeedbackRequest: SelfLearningFeedbackRequest): boolean {
        return selfLearningFeedbackRequest.requestDateTime !== undefined && selfLearningFeedbackRequest.successful === false;
    }

    // if there is a result - then the request was successful
    public static isCompletedAndSuccessful(selfLearningFeedbackRequest: SelfLearningFeedbackRequest): boolean {
        return selfLearningFeedbackRequest.result !== undefined;
    }
}
