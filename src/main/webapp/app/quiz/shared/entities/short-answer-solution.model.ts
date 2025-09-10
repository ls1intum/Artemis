import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { BaseEntityWithTempId, CanBecomeInvalid } from 'app/quiz/shared/entities/drop-location.model';

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
