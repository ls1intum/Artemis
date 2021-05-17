import { GradeStep } from 'app/entities/grade-step.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class GradingScale implements BaseEntity {
    public id?: number;
    public gradeType: GradeType = GradeType.NONE;
    public gradeSteps: GradeStep[];

    constructor(gradeType: GradeType = GradeType.GRADE, gradeSteps: GradeStep[] = []) {
        this.gradeType = gradeType;
        this.gradeSteps = gradeSteps;
    }
}

export enum GradeType {
    NONE = 'NONE',
    GRADE = 'GRADE',
    BONUS = 'BONUS',
}
