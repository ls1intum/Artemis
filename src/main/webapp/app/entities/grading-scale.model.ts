import { GradeStep } from 'app/entities/grade-step.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

export class GradingScale implements BaseEntity {
    public id?: number;
    public gradeType: GradeType = GradeType.NONE;
    public gradeSteps: GradeStep[];
    public plagiarismGrade?: string;
    public noParticipationGrade?: string;
    public GradeStep?: GradeStep;
    public course?: Course;
    public exam?: Exam;

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
