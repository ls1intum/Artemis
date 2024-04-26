import { Result } from 'app/entities/result.model';

type Points = number;

export class ResultWithPointsPerGradingCriterion {
    result: Result;
    totalPoints: Points;
}
