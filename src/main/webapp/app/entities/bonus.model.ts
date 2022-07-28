import { BaseEntity } from 'app/shared/model/base-entity';
import { GradingScale } from 'app/entities/grading-scale.model';

export class Bonus implements BaseEntity {
    public id?: number;
    public bonusStrategy?: BonusStrategy;
    public calculationSign?: number;
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
    constructor(
        public examStudentPoints: number,
        public bonusStudentPoints: number | undefined,
        public examGrade?: number | string,
        public bonusGrade?: number | string,
        public calculatedBonus?: number,
        public finalPoints?: number,
        public finalGrade?: number | string,
    ) {}
}
