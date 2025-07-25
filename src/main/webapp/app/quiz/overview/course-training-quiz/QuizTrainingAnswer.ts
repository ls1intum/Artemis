import { BaseEntity } from 'app/shared/model/base-entity';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';

export class QuizTrainingAnswer implements BaseEntity {
    public id?: number;
    public scoreInPoints?: number;
    public submittedAnswer?: SubmittedAnswer;

    constructor() {
        // scoreInPoints sollte initial undefined sein
        this.scoreInPoints = undefined;
    }
}
