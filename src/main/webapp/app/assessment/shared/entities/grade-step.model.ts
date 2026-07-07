import { BaseEntity } from 'app/foundation/model/base-entity';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStepDTO } from 'app/assessment/manage/grading/grading-service';

export interface GradeStep extends BaseEntity {
    id?: number;
    gradeName: string;
    numericValue?: number;
    lowerBoundPercentage: number;
    lowerBoundPoints?: number;
    upperBoundPercentage: number;
    upperBoundPoints?: number;
    lowerBoundInclusive: boolean;
    upperBoundInclusive: boolean;
    isPassingGrade: boolean;
}

export interface GradeDTO {
    gradeName: string;
    isPassingGrade: boolean;
    gradeType: GradeType;
}

export interface GradeStepsDTO {
    title: string;
    gradeType: GradeType;
    gradeSteps: GradeStepDTO[];
    maxPoints?: number;
    plagiarismGrade: string;
    noParticipationGrade: string;
    presentationsNumber?: number;
    presentationsWeight?: number;
}
