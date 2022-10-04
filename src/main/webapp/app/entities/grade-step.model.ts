import { BaseEntity } from 'app/shared/model/base-entity';
import { GradeType } from 'app/entities/grading-scale.model';

export class GradeStep implements BaseEntity {
    public id?: number;
    public gradeName: string;
    public numericValue?: number;
    public lowerBoundPercentage: number;
    public lowerBoundPoints?: number;
    public upperBoundPercentage: number;
    public upperBoundPoints?: number;
    public lowerBoundInclusive = true;
    public upperBoundInclusive = false;
    public isPassingGrade = false;
}

export class GradeDTO {
    public gradeName: string;
    public isPassingGrade = false;
    public gradeType: GradeType;
}

export class GradeStepsDTO {
    public title: string;
    public gradeType: GradeType;
    public gradeSteps: GradeStep[];
    public maxPoints?: number;
}
