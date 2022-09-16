import { BaseEntity } from 'app/shared/model/base-entity';
import { GradingScale } from 'app/entities/grading-scale.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

export class Bonus implements BaseEntity {
    public id?: number;
    public bonusStrategy?: BonusStrategy;
    public weight?: number;
    public sourceGradingScale?: GradingScale;
    public bonusToGradingScale?: GradingScale;

    constructor() {}
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

    constructor(public studentPointsOfBonusTo: number, public studentPointsOfBonusSource: number | undefined) {}
}

export class BonusResult {
    public bonusStrategy?: BonusStrategy;
    public bonusFromTitle?: string;
    public studentPointsOfBonusSource?: number | undefined;
    public bonusGrade?: number;
    public finalPoints?: number;
    public finalGrade?: number | string;
    public mostSeverePlagiarismVerdict?: PlagiarismVerdict;

    constructor() {}
}
