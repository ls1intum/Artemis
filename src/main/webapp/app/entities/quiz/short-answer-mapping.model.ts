import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ShortAnswerMapping implements BaseEntity, CanBecomeInvalid {
    public id?: number;
    public shortAnswerSpotIndex?: number;
    public shortAnswerSolutionIndex?: number;
    public invalid = false; // default value
    public solution?: ShortAnswerSolution;
    public spot?: ShortAnswerSpot;
    public question?: ShortAnswerQuestion;

    constructor(spot: ShortAnswerSpot | undefined, solution: ShortAnswerSolution | undefined) {
        this.spot = spot;
        this.solution = solution;
    }
}
