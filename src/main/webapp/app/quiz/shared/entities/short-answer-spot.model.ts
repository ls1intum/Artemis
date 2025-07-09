import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { BaseEntityWithTempId, CanBecomeInvalid } from 'app/quiz/shared/entities/drop-location.model';

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
