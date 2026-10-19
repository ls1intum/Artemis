import { Result } from 'app/exercise/shared/entities/result/result.model';

type GradingCriterionId = number;
type Points = number;

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ResultWithPointsPerGradingCriterion {
    result!: Result;
    totalPoints!: Points;
    // type union is needed here, because deserialized java maps are actually represented as plain JSON key-value pairs
    pointsPerCriterion!: { [key: string]: number } | Map<GradingCriterionId, Points>;
}
