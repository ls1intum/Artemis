import { Feedback } from 'app/entities/feedback.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class LongFeedbackText implements BaseEntity {
    public id?: number;
    public text?: string;
    public feedback?: Feedback;
}
