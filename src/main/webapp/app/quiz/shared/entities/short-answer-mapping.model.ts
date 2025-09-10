import { BaseEntity } from 'app/shared/model/base-entity';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { CanBecomeInvalid } from 'app/quiz/shared/entities/drop-location.model';

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
