import { BaseEntity } from 'app/shared/model/base-entity';
import { GradingScale } from 'app/entities/grading-scale.model';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';

export class Bonus implements BaseEntity {
    public id?: number;
    public bonusStrategy?: BonusStrategy;
    public weight?: number;
    public source?: GradingScale;

    // public target?: GradingScale; // TODO: Ata: Remove

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

    constructor(public studentPointsOfBonusTo: number, public studentPointsOfBonusSource: number | undefined) {}
}
