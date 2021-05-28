import { BaseEntity } from 'app/shared/model/base-entity';
import { GradeType } from 'app/entities/grading-scale.model';

export class GradeStep implements BaseEntity {
    public id?: number;
    public gradeName: string;
    public lowerBoundPercentage: number;
    public upperBoundPercentage: number;
    public lowerBoundInclusive = true;
    public upperBoundInclusive = false;
    public isPassingGrade = false;
}

export class GradeDTO {
    public gradeName: string;
    public isPassingGrade = false;
    public gradeType: GradeType;
}
