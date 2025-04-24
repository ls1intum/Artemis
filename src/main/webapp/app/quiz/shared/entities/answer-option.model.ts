import { BaseEntity } from 'app/shared/model/base-entity';
import { ExerciseHintExplanationInterface } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { CanBecomeInvalid } from 'app/quiz/shared/entities/drop-location.model';

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
