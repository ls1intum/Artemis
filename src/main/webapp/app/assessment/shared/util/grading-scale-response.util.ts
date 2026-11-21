import { BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { GradeStep, GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import type { GradeStep as GradeStepResponse } from 'app/openapi/model/grade-step';
import type { GradeSteps } from 'app/openapi/model/grade-steps';
import type { GradingScale as GradingScaleResponse } from 'app/openapi/model/grading-scale';
import type { GradingScaleUpdate } from 'app/openapi/model/grading-scale-update';

export const gradeTypeByResponse: Record<NonNullable<GradeSteps['gradeType']>, GradeType> = {
    NONE: GradeType.NONE,
    GRADE: GradeType.GRADE,
    BONUS: GradeType.BONUS,
};

const gradeTypeToRequest: Record<GradeType, GradingScaleUpdate['gradeType']> = {
    [GradeType.NONE]: 'NONE',
    [GradeType.GRADE]: 'GRADE',
    [GradeType.BONUS]: 'BONUS',
};

const bonusStrategyByResponse: Record<NonNullable<GradingScaleResponse['bonusStrategy']>, BonusStrategy> = {
    GRADES_CONTINUOUS: BonusStrategy.GRADES_CONTINUOUS,
    GRADES_DISCRETE: BonusStrategy.GRADES_DISCRETE,
    POINTS: BonusStrategy.POINTS,
};

const bonusStrategyToRequest: Record<BonusStrategy, NonNullable<GradingScaleUpdate['bonusStrategy']>> = {
    [BonusStrategy.GRADES_CONTINUOUS]: 'GRADES_CONTINUOUS',
    [BonusStrategy.GRADES_DISCRETE]: 'GRADES_DISCRETE',
    [BonusStrategy.POINTS]: 'POINTS',
};

// The server serializes the bounds and the passing flag of every grade step, so the optional fields of the schema are always present.
export function toGradeStep(step: GradeStepResponse): GradeStep {
    return {
        id: step.id,
        gradeName: step.gradeName,
        lowerBoundPercentage: step.lowerBoundPercentage ?? 0,
        lowerBoundInclusive: step.lowerBoundInclusive ?? false,
        upperBoundPercentage: step.upperBoundPercentage ?? 0,
        upperBoundInclusive: step.upperBoundInclusive ?? false,
        isPassingGrade: step.isPassingGrade ?? false,
    };
}

/**
 * Builds the grade steps the client works with; the server leaves out an empty set of grade steps.
 */
export function toGradeStepsDTO(response: GradeSteps): GradeStepsDTO {
    return {
        title: response.title,
        gradeType: response.gradeType && gradeTypeByResponse[response.gradeType],
        gradeSteps: response.gradeSteps?.map(toGradeStep) ?? [],
        maxPoints: response.maxPoints,
        plagiarismGrade: response.plagiarismGrade,
        noParticipationGrade: response.noParticipationGrade,
        presentationsNumber: response.presentationsNumber,
        presentationsWeight: response.presentationsWeight,
    };
}

/**
 * Builds the grading scale the editor works with from the grading scale response; the course or exam it belongs to is not part of the response.
 */
export function toGradingScale(response: GradingScaleResponse, course?: Course, exam?: Exam): GradingScale {
    const gradeSteps = toGradeStepsDTO(response.gradeSteps);
    const gradingScale = new GradingScale(gradeSteps.gradeType, gradeSteps.gradeSteps);
    gradingScale.id = response.id;
    gradingScale.plagiarismGrade = gradeSteps.plagiarismGrade;
    gradingScale.noParticipationGrade = gradeSteps.noParticipationGrade;
    gradingScale.presentationsNumber = gradeSteps.presentationsNumber;
    gradingScale.presentationsWeight = gradeSteps.presentationsWeight;
    gradingScale.course = course;
    gradingScale.exam = exam;
    gradingScale.bonusStrategy = response.bonusStrategy && bonusStrategyByResponse[response.bonusStrategy];
    return gradingScale;
}

/**
 * Picks what the server reads from the grading scale and its course or exam.
 */
export function toGradingScaleUpdate(gradingScale: GradingScale): GradingScaleUpdate {
    return {
        gradeType: gradeTypeToRequest[gradingScale.gradeType],
        bonusStrategy: gradingScale.bonusStrategy && bonusStrategyToRequest[gradingScale.bonusStrategy],
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
        // The server treats omission as unchanged; explicitly disable Basic when switching to None or Graded.
        coursePresentationScore: gradingScale.course ? (gradingScale.course.presentationScore ?? 0) : undefined,
        examMaxPoints: gradingScale.exam?.examMaxPoints,
    };
}
