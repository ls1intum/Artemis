import { BaseEntity } from 'app/shared/model/base-entity';
import { TextHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';

export class AnswerOption implements BaseEntity, TextHintExplanationInterface {
    public id?: number;
    public text?: string;
    public hint?: string;
    public explanation?: string;
    public isCorrect?: boolean;
    public question?: MultipleChoiceQuestion;
    public invalid?: boolean;

    constructor() {
        this.isCorrect = false; // default value
        this.invalid = false; // default value
    }
}
