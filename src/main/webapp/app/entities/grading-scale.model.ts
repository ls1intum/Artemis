import { GradeStep } from 'app/entities/grade-step.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

export class GradingScale {
    id?: number;
    course?: Course;
    exam?: Exam;
    gradeType: GradeType = GradeType.NONE;
    gradeSteps: GradeStep[];
}

export const enum GradeType {
    NONE = 'NONE',
    GRADE = 'GRADE',
    BONUS = 'BONUS',
}
