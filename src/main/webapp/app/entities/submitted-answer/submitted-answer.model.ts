import { BaseEntity } from 'app/shared';
import { Question, QuestionType } from '../question';
import { QuizSubmission } from '../quiz-submission';

export abstract class SubmittedAnswer implements BaseEntity {
    public id: number;
    public scoreInPoints: number;
    public question: Question;
    public submission: QuizSubmission;

    private type: QuestionType;

    protected constructor(type: QuestionType) {
        this.type = type;
    }
}
