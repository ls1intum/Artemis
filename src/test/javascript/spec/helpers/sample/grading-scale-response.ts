import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import type { GradingScale as GradingScaleResponse } from 'app/openapi/model/grading-scale';

/**
 * Builds the response the server sends for a grading scale, the way {@link toGradingScale} reads it back.
 */
export function toGradingScaleResponse(entity: GradingScale): GradingScaleResponse {
    return {
        id: entity.id,
        gradeSteps: {
            title: entity.exam?.title ?? entity.course?.title ?? '',
            gradeType: entity.gradeType,
            gradeSteps: entity.gradeSteps.map((step) => ({ ...step })),
            maxPoints: entity.exam?.examMaxPoints ?? entity.course?.maxPoints ?? 0,
            plagiarismGrade: entity.plagiarismGrade ?? GradingScale.DEFAULT_PLAGIARISM_GRADE,
            noParticipationGrade: entity.noParticipationGrade ?? GradingScale.DEFAULT_NO_PARTICIPATION_GRADE,
            presentationsNumber: entity.presentationsNumber,
            presentationsWeight: entity.presentationsWeight,
        },
        bonusStrategy: entity.bonusStrategy,
    };
}
