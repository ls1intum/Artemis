import { Result } from 'app/entities/result.model';

type GradingCriterionId = number;
type Points = number;

export class ResultWithPointsPerGradingCriterion {
    result: Result;
    totalPoints: Points;
    pointsPerCriterion: Map<GradingCriterionId, Points>;
}
