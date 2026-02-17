import { BaseEntity } from 'app/shared/model/base-entity';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

export class Bonus implements BaseEntity {
    public id?: number;
    public bonusStrategy?: BonusStrategy;
    public weight?: number;
    public sourceGradingScale?: GradingScale;
    public bonusToGradingScale?: GradingScale;
}

export enum BonusStrategy {
    GRADES_CONTINUOUS = 'GRADES_CONTINUOUS',
    GRADES_DISCRETE = 'GRADES_DISCRETE',
    POINTS = 'POINTS',
}

export class BonusExample {
    public examGrade?: number | string;
    public bonusGrade?: number;
    public finalPoints?: number;
    public finalGrade?: number | string;
    public exceedsMax = false;

    constructor(
        public studentPointsOfBonusTo: number,
        public studentPointsOfBonusSource: number | undefined,
    ) {}
}

export class BonusResult {
    public bonusStrategy?: BonusStrategy;
    public bonusFromTitle?: string;
    public studentPointsOfBonusSource?: number | undefined;
    public bonusGrade?: number | string;
    public finalPoints?: number;
    public finalGrade?: number | string;
    public mostSeverePlagiarismVerdict?: PlagiarismVerdict;
    public achievedPresentationScore?: number;
    public presentationScoreThreshold?: number;
}

export class BonusDTO {
    public id: number;
    public sourceGradingScaleId: number;
    public weight?: number;
}
