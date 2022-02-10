import { BaseEntity } from 'app/shared/model/base-entity';
import { TextHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';

export class AnswerOption implements BaseEntity, CanBecomeInvalid, TextHintExplanationInterface {
    public id?: number;
    public text?: string;
    public hint?: string;
    public explanation?: string;
    public isCorrect?: boolean;
    public question?: MultipleChoiceQuestion;
    public invalid = false; // default value

    constructor() {
        this.isCorrect = false; // default value
    }
}
