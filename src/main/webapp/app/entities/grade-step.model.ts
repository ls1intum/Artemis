export class GradeStep {
    id?: number;
    gradeName: string;
    lowerBoundPercentage: number;
    upperBoundPercentage: number;
    lowerBoundInclusive = true;
    upperBoundInclusive = false;
    isPassingGrade = false;
}
