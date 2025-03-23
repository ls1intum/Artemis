import { Result } from 'app/exercise/entities/result.model';

type GradingCriterionId = number;
type Points = number;

export class ResultWithPointsPerGradingCriterion {
    result: Result;
    totalPoints: Points;
    // type union is needed here, because deserialized java maps are actually represented as plain JSON key-value pairs
    pointsPerCriterion: { [key: string]: number } | Map<GradingCriterionId, Points>;
}
