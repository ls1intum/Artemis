import { BaseEntityWithTempId, CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';

export class ShortAnswerSpot extends BaseEntityWithTempId implements CanBecomeInvalid {
    public width?: number;
    public spotNr?: number;
    public invalid = false; // default value
    public question?: ShortAnswerQuestion;

    public posX?: number;
    public posY?: number;

    constructor() {
        super();
    }
}
