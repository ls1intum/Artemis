import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { BaseEntityWithTempId, CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';

export class ShortAnswerSolution extends BaseEntityWithTempId implements CanBecomeInvalid {
    public text?: string;
    public invalid = false; // default value
    public question?: ShortAnswerQuestion;

    public posX?: number;
    public posY?: number;

    constructor() {
        super();
    }
}
