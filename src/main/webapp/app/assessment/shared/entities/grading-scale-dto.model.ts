import { BonusDTO } from 'app/assessment/shared/entities/bonus.model';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

/**
 * DTO for grading scale response.
 */
export interface GradingScaleDTO {
    id?: number;
    gradeSteps: GradeStepsDTO;
    bonusStrategy?: string;
    bonusFrom?: BonusDTO[];
}

export function toEntity(dto: GradingScaleDTO, course?: Course, exam?: Exam): GradingScale {
    const entity = new GradingScale(dto.gradeSteps.gradeType, dto.gradeSteps.gradeSteps);
    entity.id = dto.id;
    entity.plagiarismGrade = dto.gradeSteps.plagiarismGrade;
    entity.noParticipationGrade = dto.gradeSteps.noParticipationGrade;
    entity.presentationsNumber = dto.gradeSteps.presentationsNumber;
    entity.presentationsWeight = dto.gradeSteps.presentationsWeight;
    entity.course = course;
    entity.exam = exam;
    entity.bonusStrategy = dto.bonusStrategy;

    return entity;
}

export function toGradingScaleDTO(entity?: GradingScale): GradingScaleDTO {
    if (!entity) {
        throw new Error('GradingScale must be defined when converting to DTO');
    }

    const gradeStepsDTO: GradeStepsDTO = {
        title: entity.exam?.title ?? entity.course?.title ?? '',
        gradeType: entity.gradeType,
        gradeSteps: entity.gradeSteps ?? [],
        maxPoints: entity.exam?.examMaxPoints ?? entity.course?.maxPoints ?? 0,
        plagiarismGrade: entity.plagiarismGrade ?? '',
        noParticipationGrade: entity.noParticipationGrade ?? '',
        presentationsNumber: entity.presentationsNumber,
        presentationsWeight: entity.presentationsWeight,
    };

    return {
        id: entity.id,
        gradeSteps: gradeStepsDTO,
        bonusStrategy: entity.bonusStrategy,
        bonusFrom: [],
    };
}
