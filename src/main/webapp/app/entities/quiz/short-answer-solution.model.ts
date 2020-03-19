import { BaseEntity } from 'app/shared/model/base-entity';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { generate } from 'app/exercises/quiz/manage/temp-id';

export class ShortAnswerSolution implements BaseEntity {
    public id: number;
    public text: string;
    public invalid = false; // default value
    public question: ShortAnswerQuestion;

    // additionally added after database changes with Stephan for visual mode
    public posX: number;
    public posY: number;
    public tempID: number;

    constructor() {
        this.tempID = generate();
    }
}
