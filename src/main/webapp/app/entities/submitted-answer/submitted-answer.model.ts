import { BaseEntity } from 'app/shared';
import { QuizQuestion, QuizQuestionType } from '../quiz-question';
import { QuizSubmission } from '../quiz-submission';

export abstract class SubmittedAnswer implements BaseEntity {
    public id: number;
    public scoreInPoints: number;
    public question: QuizQuestion;
    public submission: QuizSubmission;

    private type: QuizQuestionType;

    protected constructor(type: QuizQuestionType) {
        this.type = type;
    }
}
