import { ShortAnswerSolution } from '../short-answer-solution';
import { ShortAnswerSpot } from '../short-answer-spot';
import { ShortAnswerQuestion } from '../short-answer-question';
import { BaseEntity } from 'app/shared';

export class ShortAnswerMapping implements BaseEntity {
    public id: number;
    public shortAnswerSpotIndex: number;
    public shortAnswerSolutionIndex: number;
    public invalid = false; // default value
    public solution: ShortAnswerSolution;
    public spot: ShortAnswerSpot;
    public question: ShortAnswerQuestion;

    constructor(spot: ShortAnswerSpot, solution: ShortAnswerSolution) {
        this.spot = spot;
        this.solution = solution;
    }
}
