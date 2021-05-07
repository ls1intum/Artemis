import { BaseEntity } from 'app/shared/model/base-entity';

export class GradeStep implements BaseEntity {
    public id?: number;
    public gradeName: string;
    public lowerBoundPercentage: number;
    public upperBoundPercentage: number;
    public lowerBoundInclusive = true;
    public upperBoundInclusive = false;
    public isPassingGrade = false;
}
