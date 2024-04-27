import { BaseEntity } from 'app/shared/model/base-entity';
import { ExerciseHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';
import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';

export class AnswerOption implements BaseEntity, CanBecomeInvalid, ExerciseHintExplanationInterface {
    public id?: number;
    public text?: string;
    public hint?: string;
    public explanation?: string;
    public isCorrect?: boolean;
    public invalid = false; // default value

    constructor() {
        this.isCorrect = false; // default value
        this.text = ''; // default value
    }
}
