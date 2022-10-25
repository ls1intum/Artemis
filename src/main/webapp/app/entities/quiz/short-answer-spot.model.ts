import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { BaseEntityWithTempId, CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';

export enum SpotType {
    NUMBER = 'NUMBER',
    TEXT = 'TEXT',
}

export class ShortAnswerSpot extends BaseEntityWithTempId implements CanBecomeInvalid {
    public width?: number;
    public spotNr?: number;
    public invalid = false; // default value
    public question?: ShortAnswerQuestion;
    public type: SpotType = SpotType.TEXT; // default value

    public posX?: number;
    public posY?: number;

    constructor() {
        super();
    }
}
