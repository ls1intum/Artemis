import { GradeStep } from 'app/entities/grade-step.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

export class GradingScale implements BaseEntity {
    public id?: number;
    public gradeType: GradeType = GradeType.NONE;
    public usesPoints = false;
    public gradeSteps: GradeStep[];
    public course?: Course;
    public exam?: Exam;

    constructor(gradeType: GradeType = GradeType.GRADE, gradeSteps: GradeStep[] = [], usesPoints = false) {
        this.gradeType = gradeType;
        this.gradeSteps = gradeSteps;
        this.usesPoints = usesPoints;
    }
}

export enum GradeType {
    NONE = 'NONE',
    GRADE = 'GRADE',
    BONUS = 'BONUS',
}
