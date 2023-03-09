import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ExerciseHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class AnswerOption implements BaseEntity, CanBecomeInvalid, ExerciseHintExplanationInterface {
    public id?: number;
    public text?: string;
    public hint?: string;
    public explanation?: string;
    public isCorrect?: boolean;
    public question?: MultipleChoiceQuestion;
    public invalid = false; // default value

    constructor() {
        this.isCorrect = false; // default value
        this.text = ''; // default value
    }
}
