import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';

/**
 * DTO for updating a grading scale.
 */
export interface GradingScaleRequestDTO {
    gradeType: GradeType;
    bonusStrategy?: string;
    plagiarismGrade?: string;
    noParticipationGrade?: string;
    presentationsNumber?: number;
    presentationsWeight?: number;
    gradeSteps: GradeStepDTO[];
    courseMaxPoints?: number;
    coursePresentationScore?: number;
    examMaxPoints?: number;
}

/**
 * DTO for a grade step within a grading scale.
 */
export interface GradeStepDTO {
    lowerBoundPercentage: number;
    lowerBoundInclusive: boolean;
    upperBoundPercentage: number;
    upperBoundInclusive: boolean;
    gradeName: string;
    isPassingGrade: boolean;
}

/**
 * Converts a GradingScale to an update DTO for sending to the server.
 */
export function toUpdateDTO(gradingScale: GradingScale): GradingScaleRequestDTO {
    return {
        gradeType: gradingScale.gradeType,
        bonusStrategy: gradingScale.bonusStrategy,
        plagiarismGrade: gradingScale.plagiarismGrade,
        noParticipationGrade: gradingScale.noParticipationGrade,
        presentationsNumber: gradingScale.presentationsNumber,
        presentationsWeight: gradingScale.presentationsWeight,
        gradeSteps: gradingScale.gradeSteps.map((step) => ({
            lowerBoundPercentage: step.lowerBoundPercentage,
            lowerBoundInclusive: step.lowerBoundInclusive,
            upperBoundPercentage: step.upperBoundPercentage,
            upperBoundInclusive: step.upperBoundInclusive,
            gradeName: step.gradeName,
            isPassingGrade: step.isPassingGrade,
        })),
        courseMaxPoints: gradingScale.course?.maxPoints,
        coursePresentationScore: gradingScale.course?.presentationScore,
        examMaxPoints: gradingScale.exam?.examMaxPoints,
    };
}
