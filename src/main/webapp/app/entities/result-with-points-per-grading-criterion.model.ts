import { Result } from 'app/entities/result.model';

type GradingCriterionId = number;
type Points = number;

export class ResultWithPointsPerGradingCriterion {
    result: Result;
    points: Map<GradingCriterionId, Points>;
}
