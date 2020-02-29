import { BaseEntity } from 'app/shared/model/base-entity';
import { TextHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';

export class AnswerOption implements BaseEntity, TextHintExplanationInterface {
    public id: number;
    public text: string | null;
    public hint: string | null;
    public explanation: string | null;
    public isCorrect = false; // default value
    public question: MultipleChoiceQuestion;
    public invalid = false; // default value

    constructor() {}
}
